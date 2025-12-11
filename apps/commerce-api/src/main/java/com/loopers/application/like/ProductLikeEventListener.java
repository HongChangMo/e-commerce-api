package com.loopers.application.like;

import com.loopers.domain.like.ProductLike;
import com.loopers.domain.like.ProductLikeService;
import com.loopers.domain.like.event.ProductLikeAddedEvent;
import com.loopers.domain.like.event.ProductLikeRemovedEvent;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductLikeEventListener {

    private final ProductService productService;
    private final ProductLikeService productLikeService;

    /**
     * 좋아요 추가 시 집계 처리
     * - 집계 이벤트는 별도 트랜잭션으로 처리( 집계 로직의 성공/실패와 상관 없이, 좋아요 처리는 정상적으로 완료되어야함 )
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleProductLikeAdded(ProductLikeAddedEvent event) {
        log.info("좋아요 추가 집계 처리 이벤트 시작 - ProductId: {}", event.getProductId());

        Product product = productService.getProductById(event.getProductId());
        ProductLike like = productLikeService.getProductLikeById(event.getLikeId());

        // 좋아요 처리
        product.incrementLikeCount(like);

        log.info("좋아요 추가 집계 처리 이벤트 완료 - ProductId: {}, 현재 좋아요 수: {}",
                event.getProductId(), product.getLikeCount());
    }

    /**
     * 좋아요 취소 시 집계 처리
     * - 집계 이벤트는 별도 트랜잭션으로 처리( 집계 로직의 성공/실패와 상관 없이, 좋아요 처리는 정상적으로 완료되어야함 )
     * */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleProductLikeRemoved(ProductLikeRemovedEvent event) {
        log.info("좋아요 취소 집계 처리 시작 - ProductId: {}", event.getProductId());

        Product product = productService.getProductById(event.getProductId());
        ProductLike like = productLikeService.getProductLikeById(event.getLikeId());

        product.decrementLikeCount(like);

        log.info("좋아요 취소 집계 처리 이벤트 완료 - ProductId: {}, 현재 좋아요 수: {}",
                event.getProductId(), product.getLikeCount());
    }
}
