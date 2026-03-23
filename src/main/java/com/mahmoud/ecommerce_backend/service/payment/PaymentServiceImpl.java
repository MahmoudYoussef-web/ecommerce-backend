package com.mahmoud.ecommerce_backend.service.payment;

import com.mahmoud.ecommerce_backend.dto.payment.PaymentResponse;
import com.mahmoud.ecommerce_backend.entity.Order;
import com.mahmoud.ecommerce_backend.entity.Payment;
import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.enums.PaymentMethod;
import com.mahmoud.ecommerce_backend.enums.PaymentStatus;
import com.mahmoud.ecommerce_backend.enums.OrderStatus;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ForbiddenException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.mapper.PaymentMapper;
import com.mahmoud.ecommerce_backend.repository.OrderRepository;
import com.mahmoud.ecommerce_backend.repository.PaymentRepository;
import com.mahmoud.ecommerce_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentMapper paymentMapper;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public PaymentResponse createPayment(Long orderId, PaymentMethod method) {

        if (orderId == null) {
            throw new BadRequestException("OrderId must not be null");
        }

        if (method == null) {
            throw new BadRequestException("Payment method must not be null");
        }

        User user = getCurrentUser();

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("Order not found id={}", orderId);
                    return new ResourceNotFoundException("Order not found");
                });

        if (!order.getUser().getId().equals(user.getId())) {
            log.warn("Unauthorized payment attempt userId={}, orderId={}", user.getId(), orderId);
            throw new ForbiddenException("You are not allowed to pay for this order");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Order is not eligible for payment");
        }

        if (paymentRepository.findByOrderId(orderId).isPresent()) {
            throw new BadRequestException("Payment already exists for this order");
        }

        Payment payment = Payment.builder()
                .order(order)
                .paymentMethod(method)
                .amount(order.getTotalAmount())
                .status(PaymentStatus.INITIATED)
                .build();

        paymentRepository.save(payment);

        log.info("Payment initiated orderId={}, userId={}", orderId, user.getId());

        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse updateStatus(Long paymentId, PaymentStatus status) {

        if (paymentId == null) {
            throw new BadRequestException("PaymentId must not be null");
        }

        if (status == null) {
            throw new BadRequestException("Payment status must not be null");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> {
                    log.warn("Payment not found id={}", paymentId);
                    return new ResourceNotFoundException("Payment not found");
                });

        PaymentStatus currentStatus = payment.getStatus();


        if (currentStatus == status) {
            log.info("Duplicate status update ignored paymentId={}, status={}", paymentId, status);
            return paymentMapper.toResponse(payment);
        }

        if (!isValidTransition(currentStatus, status)) {
            throw new BadRequestException(
                    "Invalid payment status transition from " + currentStatus + " to " + status
            );
        }

        payment.setStatus(status);

        if (status == PaymentStatus.COMPLETED) {
            payment.setPaidAt(Instant.now());
            payment.getOrder().setStatus(OrderStatus.CONFIRMED);

            log.info("Payment completed orderId={}", payment.getOrder().getId());
        }

        if (status == PaymentStatus.FAILED) {
            payment.setFailureReason("Payment failed at " + Instant.now());
            log.warn("Payment failed orderId={}", payment.getOrder().getId());
        }

        return paymentMapper.toResponse(payment);
    }

    private boolean isValidTransition(PaymentStatus current, PaymentStatus next) {

        return switch (current) {
            case PENDING -> next == PaymentStatus.INITIATED;
            case INITIATED -> next == PaymentStatus.COMPLETED
                    || next == PaymentStatus.FAILED
                    || next == PaymentStatus.CANCELLED;
            case COMPLETED -> next == PaymentStatus.REFUNDED
                    || next == PaymentStatus.PARTIALLY_REFUNDED;
            case FAILED, CANCELLED, EXPIRED -> false;
            case REFUNDED, PARTIALLY_REFUNDED -> false;
        };
    }

    private User getCurrentUser() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getName() == null) {
            throw new ForbiddenException("Unauthenticated user");
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}