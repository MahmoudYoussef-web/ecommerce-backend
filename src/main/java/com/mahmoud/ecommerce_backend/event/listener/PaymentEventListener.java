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

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final OrderRepository orderRepository;

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {

        try {

            Order order = orderRepository.findById(event.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found"));


            if (order.getStatus() == OrderStatus.PAID) {
                log.info("Order already PAID, skipping orderId={}", order.getId());
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