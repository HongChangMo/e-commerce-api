package com.loopers.domain.order.event;

import com.loopers.domain.payment.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderCreatedEvent(
        Long orderId,
        Long userId,
        BigDecimal totalPrice,
        PaymentType paymentType,
        List<OrderItem> items,
        LocalDateTime createdAt
) {
    public record OrderItem(
            Long productId,
            Integer quantity
    ) {}

    public static OrderCreatedEvent of(
            Long orderId,
            Long userId,
            BigDecimal totalPrice,
            PaymentType paymentType,
            List<OrderItem> items
    ) {
        return new OrderCreatedEvent(
                orderId,
                userId,
                totalPrice,
                paymentType,
                items,
                LocalDateTime.now()
        );
    }
}
