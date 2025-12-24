package com.loopers.application.like;

import com.loopers.application.eventhandled.EventHandledFacade;
import com.loopers.application.eventhandled.EventHandledInfo;
import com.loopers.application.metrics.ProductMetricsDailyFacade;
import com.loopers.application.metrics.ProductMetricsFacade;
import com.loopers.interfaces.consumer.like.dto.ProductLikeEvent;
import com.loopers.kafka.AggregateTypes;
import com.loopers.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductLikeBatchEventHandler {

    private final EventHandledFacade eventHandledFacade;
    private final ProductMetricsFacade productMetricsFacade;
    private final ProductMetricsDailyFacade productMetricsDailyFacade;

    public void handleProductLikeBatch(List<ProductLikeEvent> events) {
        // 1. 미처리 이벤트만 필터링 (배치 간 중복 방지)
        List<ProductLikeEvent> unprocessedEvents = events.stream()
                .filter(event -> {
                    if (eventHandledFacade.isAlreadyHandled(event.eventId())) {
                        log.info("이미 처리된 이벤트 스킵 - eventId: {}", event.eventId());
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        if (unprocessedEvents.isEmpty()) {
            log.info("처리할 이벤트 없음 (모두 중복)");
            return;
        }

        // 2. 같은 배치 내 중복 제거
        Map<String, ProductLikeEvent> uniqueEvents = unprocessedEvents.stream()
                .collect(Collectors.toMap(
                        ProductLikeEvent::eventId,
                        event -> event,
                        (existing, replacement) -> existing // 중복 시 기존 것 유지
                ));

        // 3. 증감량 계산
        Map<Long, Integer> likeDeltas = new HashMap<>();

        for(ProductLikeEvent event : uniqueEvents.values()) {
            Long productId = event.productId();
            String eventType = event.eventType();

            int delta = 0;
            if (KafkaTopics.ProductLike.LIKE_ADDED.equals(eventType)) {
                delta = 1;
            } else if (KafkaTopics.ProductLike.LIKE_REMOVED.equals(eventType)) {
                delta = -1;
            }

            // 증감 처리
            likeDeltas.merge(productId, delta, Integer::sum);
        }

        // 4. ProductMetrics 배치 업데이트 처리 (전체 누계만)
        productMetricsFacade.updateLikeCountBatch(likeDeltas);

        // 5. ProductMetricsDaily 배치 업데이트 처리 (일자별 증감 이력만)
        productMetricsDailyFacade.updateLikeDeltaBatch(likeDeltas, LocalDate.now());

        // 6. 처리 완료 기록 (배치로 등록)
        List<EventHandledInfo> eventHandledInfos = uniqueEvents.values().stream()
                .map(event -> EventHandledInfo.of(
                        event.eventId(),
                        event.eventType(),
                        AggregateTypes.PRODUCT_LIKE,
                        event.productId().toString()
                ))
                .collect(Collectors.toList());

        eventHandledFacade.markAsHandledBatch(eventHandledInfos);

        log.info("배치 처리 완료 - 전체: {}, 미처리: {}, 실제 처리: {}",
                events.size(), unprocessedEvents.size(), uniqueEvents.size());
    }
}
