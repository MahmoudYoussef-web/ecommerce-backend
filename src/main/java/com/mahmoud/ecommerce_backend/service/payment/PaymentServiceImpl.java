package com.mahmoud.ecommerce_backend.service.payment;

import com.mahmoud.ecommerce_backend.dto.payment.PaymentResponse;
import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.enums.*;
import com.mahmoud.ecommerce_backend.event.payment.PaymentCompletedEvent;
import com.mahmoud.ecommerce_backend.exception.*;
import com.mahmoud.ecommerce_backend.mapper.PaymentMapper;
import com.mahmoud.ecommerce_backend.repository.*;
import com.mahmoud.ecommerce_backend.service.inventory.ReservationService;
import com.mahmoud.ecommerce_backend.service.security.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentMapper paymentMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentProvider paymentProvider;
    private final SecurityService securityService;
    private final ReservationService reservationService;

    private static final String DEFAULT_CURRENCY = "USD";

    @Override
    @Transactional
    public PaymentResponse createPayment(Long orderId, PaymentMethod method) {

        User user = securityService.getCurrentUser();

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Unauthorized");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Order not payable");
        }

        if (paymentRepository.findByOrderId(orderId).isPresent()) {
            throw new BadRequestException("Payment already exists");
        }

        Payment payment = Payment.create(
                order,
                method,
                order.getTotalAmount(),
                DEFAULT_CURRENCY
        );

        paymentRepository.save(payment);

        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public void processWebhook(String eventId, Long paymentId, PaymentStatus status, String reference) {

        if (paymentRepository.existsByEventId(eventId)) return;

        Payment payment = findPayment(paymentId);

        if (!assignEventId(payment, eventId)) return;

        applyStatusChange(payment, status, reference);
    }

    @Override
    @Transactional
    public PaymentResponse updateStatus(Long paymentId, PaymentStatus status) {

        Payment payment = findPayment(paymentId);

        applyStatusChange(payment, status, null);

        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public String createCheckoutSession(Long paymentId) {

        Payment payment = findPayment(paymentId);

        User user = securityService.getCurrentUser();

        if (!payment.getOrder().getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Unauthorized");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BadRequestException("Invalid payment state");
        }

        return paymentProvider.createCheckoutSession(paymentId);
    }

    @Override
    @Transactional
    public void processStripeWebhook(String eventId,
                                     Long paymentId,
                                     PaymentStatus status,
                                     String reference,
                                     BigDecimal amount,
                                     String currency) {

        if (paymentRepository.existsByEventId(eventId)) return;

        Payment payment = findPayment(paymentId);

        if (payment.getAmount().compareTo(amount) != 0) {
            throw new ForbiddenException("Amount mismatch");
        }

        if (!payment.getCurrency().equalsIgnoreCase(currency)) {
            throw new ForbiddenException("Currency mismatch");
        }

        if (!assignEventId(payment, eventId)) return;

        applyStatusChange(payment, status, reference);
    }

    private void applyStatusChange(Payment payment, PaymentStatus status, String reference) {

        if (reference != null) {
            payment.setGatewayReference(reference);
        }

        if (status == PaymentStatus.COMPLETED) {
            handleSuccess(payment, reference);
        }

        if (status == PaymentStatus.FAILED || status == PaymentStatus.CANCELLED) {
            handleFailure(payment);
        }
    }

    private void handleSuccess(Payment payment, String reference) {

        Order order = payment.getOrder();

        payment.complete(reference);

        reservationService.confirm(order.getId());

        eventPublisher.publishEvent(
                new PaymentCompletedEvent(this, payment.getId(), order.getId())
        );
    }

    private void handleFailure(Payment payment) {

        Order order = payment.getOrder();

        payment.fail("Payment failed at " + Instant.now());

        reservationService.release(order.getId());
    }

    private boolean assignEventId(Payment payment, String eventId) {
        try {
            payment.setEventId(eventId);
            paymentRepository.flush();
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    private Payment findPayment(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }
}