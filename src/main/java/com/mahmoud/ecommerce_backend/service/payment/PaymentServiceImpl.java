package com.mahmoud.ecommerce_backend.service.payment;

import com.mahmoud.ecommerce_backend.dto.payment.PaymentResponse;
import com.mahmoud.ecommerce_backend.entity.Order;
import com.mahmoud.ecommerce_backend.entity.Payment;
import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.enums.OrderStatus;
import com.mahmoud.ecommerce_backend.enums.PaymentMethod;
import com.mahmoud.ecommerce_backend.enums.PaymentStatus;
import com.mahmoud.ecommerce_backend.event.payment.PaymentCompletedEvent;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ForbiddenException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.mapper.PaymentMapper;
import com.mahmoud.ecommerce_backend.repository.OrderRepository;
import com.mahmoud.ecommerce_backend.repository.PaymentRepository;
import com.mahmoud.ecommerce_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentMapper paymentMapper;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentProvider paymentProvider;

    @Override
    @Transactional
    public PaymentResponse createPayment(Long orderId, PaymentMethod method) {

        if (orderId == null || method == null) {
            throw new BadRequestException("Invalid request");
        }

        User user = getCurrentUser();

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Unauthorized");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Payment allowed only for PENDING orders");
        }

        if (paymentRepository.findByOrderId(orderId).isPresent()) {
            throw new BadRequestException("Payment already exists");
        }

        Payment payment = Payment.builder()
                .order(order)
                .paymentMethod(method)
                .amount(order.getTotalAmount())
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .build();

        paymentRepository.save(payment);

        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse updateStatus(Long paymentId, PaymentStatus status) {

        if (paymentId == null || status == null) {
            throw new BadRequestException("Invalid request");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        changeStatus(payment, status, null);

        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public void processWebhook(String eventId, Long paymentId, PaymentStatus status, String reference) {

        if (eventId == null || paymentId == null || status == null) {
            throw new BadRequestException("Invalid webhook payload");
        }

        if (paymentRepository.existsByEventId(eventId)) {
            log.info("Duplicate webhook ignored eventId={}", eventId);
            return;
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        try {
            payment.setEventId(eventId);
            paymentRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            log.warn("Duplicate eventId prevented eventId={}", eventId);
            return;
        }

        changeStatus(payment, status, reference);
    }

    @Transactional
    public void processStripeWebhook(String eventId,
                                     Long paymentId,
                                     PaymentStatus status,
                                     String reference,
                                     BigDecimal amount,
                                     String currency) {

        if (eventId == null || paymentId == null || status == null || amount == null || currency == null) {
            throw new BadRequestException("Invalid Stripe webhook payload");
        }

        if (paymentRepository.existsByEventId(eventId)) {
            log.info("Duplicate Stripe webhook ignored eventId={}", eventId);
            return;
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getAmount().compareTo(amount) != 0) {
            throw new ForbiddenException("Amount mismatch");
        }

        if (!payment.getCurrency().equalsIgnoreCase(currency)) {
            throw new ForbiddenException("Currency mismatch");
        }

        try {
            payment.setEventId(eventId);
            paymentRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            log.warn("Duplicate eventId prevented eventId={}", eventId);
            return;
        }

        changeStatus(payment, status, reference);
    }

    @Override
    @Transactional
    public String createCheckoutSession(Long paymentId) {

        if (paymentId == null) {
            throw new BadRequestException("PaymentId required");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        User user = getCurrentUser();

        if (!payment.getOrder().getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Unauthorized");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BadRequestException("Invalid payment state");
        }

        if (payment.getOrder().getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Order is not payable");
        }

        return paymentProvider.createCheckoutSession(paymentId);
    }

    private void changeStatus(Payment payment, PaymentStatus newStatus, String reference) {

        if (!isValidTransition(payment.getStatus(), newStatus)) {
            throw new BadRequestException("Invalid payment status transition");
        }

        payment.setStatus(newStatus);

        if (reference != null) {
            payment.setGatewayReference(reference);
        }

        if (newStatus == PaymentStatus.COMPLETED) {
            payment.setPaidAt(Instant.now());

            try {
                eventPublisher.publishEvent(
                        new PaymentCompletedEvent(this, payment.getId(), payment.getOrder().getId())
                );
            } catch (Exception ex) {
                log.error("Event publish failed paymentId={}", payment.getId(), ex);
            }
        }

        if (newStatus == PaymentStatus.FAILED) {
            payment.setFailureReason("Payment failed at " + Instant.now());
        }
    }

    private boolean isValidTransition(PaymentStatus current, PaymentStatus next) {

        return switch (current) {
            case PENDING -> next == PaymentStatus.INITIATED || next == PaymentStatus.FAILED || next == PaymentStatus.CANCELLED;
            case INITIATED -> next == PaymentStatus.COMPLETED || next == PaymentStatus.FAILED || next == PaymentStatus.CANCELLED;
            case COMPLETED -> next == PaymentStatus.REFUNDED || next == PaymentStatus.PARTIALLY_REFUNDED;
            default -> false;
        };
    }

    private User getCurrentUser() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getName() == null) {
            throw new ForbiddenException("Unauthenticated");
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}