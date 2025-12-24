package com.loopers.interfaces.consumer.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.order.OrderEventHandler;
import com.loopers.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderEventHandler orderEventHandler;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.ORDER,
            groupId = "commerce-collector-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment
    ) {
        try {
            log.info("주문 이벤트 수신 - key: {}, message: {}", key, message);

            // JSON 파싱
            JsonNode jsonNode = objectMapper.readTree(message);

            // 필수 필드 검증
            if (!jsonNode.has("eventId") || !jsonNode.has("eventType") || !jsonNode.has("payload")) {
                log.error("잘못된 메시지 형식 - 필수 필드 누락: {}", message);
                acknowledgment.acknowledge();  // 재시도 방지
                return;
            }

            String eventId = jsonNode.get("eventId").asText();
            String eventType = jsonNode.get("eventType").asText();
            JsonNode payload = jsonNode.get("payload");

            // 이벤트 타입별 처리
            if( KafkaTopics.Order.ORDER_CREATED.equals(eventType) ) {
                // 이벤트별 필드 검증 - items 배열 존재 확인
                if (!payload.has("items") || !payload.get("items").isArray()) {
                    log.error("잘못된 ORDER_CREATED 형식 - items 배열 누락 또는 잘못된 형식 - eventId: {}, payload: {}", eventId, payload);
                    acknowledgment.acknowledge();  // 재시도 방지
                    return;
                }

                // 주문 내 각 상품별로 처리
                JsonNode items = payload.get("items");
                for (int i = 0; i < items.size(); i++) {
                    JsonNode item = items.get(i);

                    // 상품별 필드 검증
                    if (!item.has("productId") || !item.has("quantity")) {
                        log.error("잘못된 OrderItem 형식 - eventId: {}, item: {}", eventId, item);
                        continue;  // 해당 상품만 스킵하고 다음 상품 처리
                    }

                    Long productId = item.get("productId").asLong();
                    int quantity = item.get("quantity").asInt();

                    // 상품별 고유 eventId 생성 (멱등성 보장)
                    String itemEventId = eventId + "-" + productId;

                    orderEventHandler.handleOrderCreated(itemEventId, productId, quantity);
                }
            }

            // 수동 커밋
            acknowledgment.acknowledge();
            log.info("주문 이벤트 처리 완료 - eventId: {}", eventId);

        } catch (JsonProcessingException e) {
            // JSON 파싱 에러 - 재시도 불필요
            log.error("JSON 파싱 실패 (재시도 안 함) - message: {}", message, e);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            // Business 로직 에러 - 재시도
            log.error("이벤트 처리 실패 (재시도) - message: {}", message, e);
            throw new RuntimeException("이벤트 처리 실패", e);
        }
    }
}
