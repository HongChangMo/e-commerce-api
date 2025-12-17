package com.loopers.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventService;
import com.loopers.infrastructure.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventService outboxEventService;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private final int QUERY_LIMIT = 200;

    @Scheduled(fixedDelay = 3000)  // 3초마다 실행하여 이벤트 발행 처리
    public void publishPendingEvents() {
        // 이벤트 발행 상태가 PENDING 인 목록을 가져온다.
        List<OutboxEvent> pendingEvents = outboxEventService.getPendingEvents(QUERY_LIMIT);

        for(OutboxEvent event : pendingEvents) {
            try {
                publishEvent(event);
            } catch (Exception e) {
                log.error("Outbox 이벤트 발행 실패 - id: {}", event.getId(), e);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishEvent(OutboxEvent outboxEvent) {
        try {
            // 토픽 결정
            String topic = determineTopicByEventType(outboxEvent.getEventType());

            // Kafka 발행
            kafkaTemplate.send(
                    topic,
                    outboxEvent.getAggregateId(),  // Partition Key
                    objectMapper.readValue(outboxEvent.getPayload(), Object.class)
            ).get();  // 동기 대기 (발행 보장)

            // 발행 성공 시 상태 업데이트
            outboxEvent.markAsPublished();
            outboxEventService.save(outboxEvent);

            log.info("Outbox 이벤트 발행 완료 - id: {}, type: {}",
                    outboxEvent.getId(), outboxEvent.getEventType());

        } catch (Exception e) {
            log.error("Kafka 발행 실패 - Outbox id: {}", outboxEvent.getId(), e);
            outboxEvent.markAsFailed();
            outboxEventService.save(outboxEvent);
        }
    }

    private String determineTopicByEventType(String eventType) {
        return switch (eventType) {
            case "LikeAdded" -> KafkaTopics.PRODUCT_LIKE_ADDED;
            case "LikeRemoved" -> KafkaTopics.PRODUCT_LIKE_REMOVED;
            case "ViewIncreased" -> KafkaTopics.PRODUCT_VIEW_INCREASED;
            case "OrderCreated" -> KafkaTopics.ORDER_CREATED;
            case "OrderCompleted" -> KafkaTopics.ORDER_COMPLETED;
            case "CouponUsed" -> KafkaTopics.COUPON_USED;
            case "UserActivity" -> KafkaTopics.USER_ACTIVITY;
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}
