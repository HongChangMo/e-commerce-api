package com.loopers.domain.coupon.event;

import com.loopers.domain.Money;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class CouponUsedEvent {
    private final Long userId;
    private final Long couponId;
    private final Long orderId;
    private final Money discountAmount;
}
