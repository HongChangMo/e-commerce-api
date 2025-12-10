package com.loopers.domain.like;

import com.loopers.domain.like.event.ProductLikeAddedEvent;
import com.loopers.domain.like.event.ProductLikeRemovedEvent;
import com.loopers.domain.product.Product;
import com.loopers.domain.user.User;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductLikeService {

    private final ProductLikeRepository productLikeRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProductLike addLike(User user, Product product) {
        // 멱등성 처리: 이미 좋아요가 존재하면 기존 것을 반환
        return productLikeRepository.findByLikeUserAndLikeProduct(user, product)
                .orElseGet(() -> {
                    // 좋아요가 없는 경우에만 새로 생성
                    ProductLike like = ProductLike.addLike(user, product);
                    ProductLike savedLike = productLikeRepository.save(like);

                    // Product의 좋아요 수(집계) 증가 이벤트 분리
                    eventPublisher.publishEvent(
                            new ProductLikeAddedEvent(savedLike.getId(), product.getId())
                    );

                    return savedLike;
                });
    }

    public void cancelLike(User user, Product product) {
        ProductLike like = productLikeRepository.findByLikeUserAndLikeProduct(user, product)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요가 존재하지 않습니다"));

        // Product의 좋아요 수 감소
        product.decrementLikeCount(like);

        eventPublisher.publishEvent(
                new ProductLikeRemovedEvent(
                        like.getId(), product.getId()
                )
        );

    }

    public ProductLike getProductLikeById(Long likeId) {
        return productLikeRepository.findById(likeId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요 정보가 존재하지 않습니다"));
    }
}
