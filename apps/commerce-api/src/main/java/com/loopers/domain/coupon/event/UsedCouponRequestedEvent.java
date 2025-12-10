package com.loopers.domain.coupon.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class UsedCouponRequestedEvent {
    private final Long userId;
    private final Long couponId;
}
