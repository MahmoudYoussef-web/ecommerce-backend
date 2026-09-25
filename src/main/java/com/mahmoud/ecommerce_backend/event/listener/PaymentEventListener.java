package com.mahmoud.ecommerce_backend.event.listener;

import com.mahmoud.ecommerce_backend.entity.Order;
import com.mahmoud.ecommerce_backend.enums.OrderStatus;
import com.mahmoud.ecommerce_backend.event.payment.PaymentCompletedEvent;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final OrderRepository orderRepository;

    @Async
    // AFTER_COMMIT: the webhook transaction must be persisted before this
    // fallback runs, otherwise it reads the pre-payment status and can
    // overwrite NEEDS_ATTENTION (paid-but-blocked) with PAID.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {

        try {

            Order order = orderRepository.findById(event.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found"));


            // Belt-and-braces fallback only: the synchronous payment path
            // already transitions PENDING -> PAID. Never touch other states —
            // in particular NEEDS_ATTENTION (paid but fulfillment blocked)
            // must not be flipped to PAID by this async listener.
            if (order.getStatus() != OrderStatus.PENDING) {
                log.info("Order not PENDING ({}), skipping orderId={}",
                        order.getStatus(), order.getId());
                return;
            }

            order.markAsPaid();

            log.info("Order status updated | orderId={} newStatus=PAID",
                    order.getId());

        } catch (Exception ex) {
            log.error("Async payment event failed orderId={}", event.getOrderId(), ex);
        }
    }
}