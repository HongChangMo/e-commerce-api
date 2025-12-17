package com.loopers.infrastructure.kafka;

public final class KafkaTopics {
    // 상품 메트릭 관련 토픽 (commerce-collector가 구독)
    public static final String PRODUCT_LIKE_ADDED = "product.like.added";
    public static final String PRODUCT_LIKE_REMOVED = "product.like.removed";
    public static final String PRODUCT_VIEW_INCREASED = "product.view.increased";

    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_COMPLETED = "order.completed";

    // 쿠폰 관련 토픽
    public static final String COUPON_USED = "order.payment.coupon.used";
    public static final String USER_ACTIVITY = "order.payment.user.activity";
    private KafkaTopics() {}
}
