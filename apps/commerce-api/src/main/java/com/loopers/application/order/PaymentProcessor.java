package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.*;
import com.loopers.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 결제 처리 컴포넌트
 *
 * 포인트 결제: 부모 트랜잭션 내에서 동기 처리
 * 카드 결제: Payment 저장은 별도 트랜잭션, PG 장애 시에도 PENDING으로 저장
 *
 * - PG 장애 시 Payment는 PENDING 상태로 저장
 * - 스케줄러가 나중에 PENDING Payment를 재확인 가능
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProcessor {

    @Value("${payment.callback.base-url}")
    private String callbackBaseUrl;

    private final TransactionTemplate transactionTemplate;
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    /**
     * 포인트 결제 처리 (부모 트랜잭션 내에서 동기 처리)
     *
     * @param user 결제할 사용자 (영속 상태)
     * @param order 결제할 주문 (영속 상태)
     *
     * 처리 순서:
     * 1. Payment 생성 (PENDING)
     * 2. 포인트 차감
     * 3. Payment 즉시 완료 (PENDING → SUCCESS)
     * 4. Payment 저장
     * 5. 주문 완료 처리 (OrderStatus.COMPLETED)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void processPointPayment(User user, Order order) {
        // 1. Payment 생성 (PENDING)
        Payment payment = Payment.createPaymentForPoint(
                order,
                order.getTotalPrice(),
                PaymentType.POINT
        );

        // 2. 포인트 차감
        user.usePoint(order.getTotalPrice());

        // 3. Payment 즉시 완료 (PENDING → SUCCESS)
        payment.completePointPayment();

        // 4. Payment 저장
        paymentService.save(payment);

        // 5. 주문 완료 처리
        order.completeOrder();
    }

    /**
     * 카드 결제 처리 (Payment 저장은 별도 트랜잭션)
     *
     * @param command 결제 정보를 담은 주문 커맨드
     * @param order 결제할 주문 (영속 상태)
     *
     * 처리 순서:
     * 1. 별도 트랜잭션에서 Payment 생성 및 저장 (TransactionTemplate 사용)
     * 2. PG 결제 요청 (비동기)
     *    - PG 즉시 실패: Payment는 PENDING 유지
     *    - PG 성공: Payment → PROCESSING
     *    - PG 호출 실패: Payment는 PENDING 유지 (Scheduler 재시도 대상)
     * 3. 주문 상태 업데이트 (부모 트랜잭션)
     *    - PROCESSING인 경우: OrderStatus.RECEIVED
     *    - PENDING인 경우: 상태 변경 없음 (나중에 처리)
     *
     * PG 장애 시에도 Payment는 PENDING으로 저장되어 Scheduler가 재시도 가능
     */
    public void processCardPayment(OrderCommand command, Order order) {
        // 1. 별도 트랜잭션에서 Payment 생성 및 저장 (TransactionTemplate 사용)
        Payment payment = transactionTemplate.execute(status -> {
            Payment p = Payment.createPaymentForCard(
                    order,
                    order.getTotalPrice(),
                    command.paymentType(),
                    command.cardType(),
                    command.cardNo()
            );
            paymentService.save(p);

            try {
                // 2. PG 결제 요청 (비동기)
                String callbackUrl = callbackBaseUrl + "/api/v1/payments/callback";
                PaymentResult result = paymentGateway.processPayment(command.userId(), p, callbackUrl);

                if ("FAIL".equals(result.status())) {
                    log.warn("PG에서 즉시 실패 응답. orderId={}, paymentId={}",
                            order.getId(), p.getPaymentId());
                    return p;
                }

                p.startProcessing(result.transactionId());
                log.info("PG 결제 요청 성공. orderId={}, paymentId={}, transactionId={}",
                        order.getId(), p.getPaymentId(), result.transactionId());

            } catch (Exception e) {
                log.error("PG 호출 실패. Payment는 PENDING으로 유지. orderId={}, paymentId={}",
                        order.getId(), p.getPaymentId(), e);
            }

            return p;
        });

        // TransactionTemplate.execute()는 항상 non-null 반환
        // 3. 주문 상태 업데이트 (부모 트랜잭션)
        if (payment.getStatus() == PaymentStatus.PROCESSING) {
            order.updateStatus(OrderStatus.RECEIVED);
        }
    }
}
