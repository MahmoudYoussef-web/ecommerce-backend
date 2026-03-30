package com.mahmoud.ecommerce_backend.event.payment;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PaymentCompletedEvent extends ApplicationEvent {

    private final Long paymentId;
    private final Long orderId;

    public PaymentCompletedEvent(Object source, Long paymentId, Long orderId) {
        super(source);
        this.paymentId = paymentId;
        this.orderId = orderId;
    }
}