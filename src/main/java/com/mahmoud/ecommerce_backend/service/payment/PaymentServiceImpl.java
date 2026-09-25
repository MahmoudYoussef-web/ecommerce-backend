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
    private final CartRepository cartRepository;
    private final PaymentMapper paymentMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentProvider paymentProvider;
    private final SecurityService securityService;
    private final ReservationService reservationService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

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

        // One payment row exists per order (DB-enforced). A FAILED/CANCELLED
        // attempt is rebased to PENDING so the customer can retry; COMPLETED
        // payments still block (no double payment for one order).
        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseGet(() -> {
                    Payment created = Payment.create(
                            order,
                            method,
                            order.getTotalAmount(),
                            DEFAULT_CURRENCY
                    );
                    return paymentRepository.save(created);
                });

        if (payment.getStatus() == PaymentStatus.COMPLETED
                || payment.getStatus() == PaymentStatus.REFUNDED
                || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BadRequestException("Payment already exists");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            payment.rebaseToPending("retry requested by customer " + user.getId());

            // The original reservations were released when the payment
            // failed. Re-reserve for the retry; if the stock is gone the
            // retry fails honestly (400) and the payment stays FAILED.
            for (OrderItem item : order.getOrderItems()) {
                reservationService.reserve(item.getProductId(), item.getQuantity(), orderId);
            }

            log.info("Payment rebased for retry | paymentId={} orderId={} method={}",
                    payment.getId(), orderId, method);
        }

        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public void processWebhook(String eventId, Long paymentId, PaymentStatus status, String reference) {

        if (paymentRepository.existsByEventId(eventId)) return;

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

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
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public String createCheckoutSession(Long paymentId) {

        // Short transaction ONLY around the ownership/state check; the Stripe
        // HTTP call then runs with NO database connection held, so external
        // latency cannot occupy the pool.
        PaymentValidation validated = transactionTemplate.execute(tx -> validateCheckout(paymentId));

        return paymentProvider.createCheckoutSession(validated.paymentId());
    }

    private record PaymentValidation(Long paymentId) {}

    private PaymentValidation validateCheckout(Long paymentId) {

        Payment payment = findPayment(paymentId);

        User user = securityService.getCurrentUser();

        if (!payment.getOrder().getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Unauthorized");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BadRequestException("Invalid payment state");
        }

        return new PaymentValidation(payment.getId());
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

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getAmount().compareTo(amount) != 0) {
            throw new ForbiddenException("Amount mismatch");
        }

        if (!payment.getCurrency().equalsIgnoreCase(currency)) {
            throw new ForbiddenException("Currency mismatch");
        }

        if (!assignEventId(payment, eventId)) return;

        applyStatusChange(payment, status, reference);
    }

    @Override
    @Transactional
    public void markCodPaid(Long orderId) {

        // Lock the order so two admins marking paid concurrently serialize.
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (order.getStatus() == OrderStatus.PAID) {
            log.info("COD mark-paid no-op | orderId={} already PAID", orderId);
            return;
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Order not payable");
        }

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseGet(() -> {
                    Payment created = Payment.create(
                            order,
                            PaymentMethod.CASH_ON_DELIVERY,
                            order.getTotalAmount(),
                            DEFAULT_CURRENCY
                    );
                    return paymentRepository.save(created);
                });

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            log.info("COD mark-paid no-op | paymentId={} already COMPLETED", payment.getId());
            return;
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BadRequestException("Invalid payment state");
        }

        String adminRef = "COD_ADMIN:" + securityService.getCurrentUser().getId();
        handleSuccess(payment, adminRef);
    }

    @Override
    @Transactional
    public void mockCompletePayment(Long paymentId) {

        User user = securityService.getCurrentUser();

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (!payment.getOrder().getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Unauthorized");
        }

        if (payment.getOrder().getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Order not payable");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BadRequestException("Invalid payment state");
        }

        String ref = "MOCK_STRIPE:" + System.currentTimeMillis();
        handleSuccess(payment, ref);
    }


    private void applyStatusChange(Payment payment, PaymentStatus status, String reference) {

        // Replay/defense-in-depth: a second event confirming an already
        // completed payment is a no-op (never double-confirm stock or money).
        if (payment.getStatus() == status) return;

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

        try {
            // Joins the current transaction. If stock can no longer cover the
            // order (reservation expired and inventory consumed elsewhere),
            // the exception is catchable WITHOUT poisoning this transaction.
            reservationService.confirmForOrder(order.getId());
        } catch (BadRequestException ex) {
            // Compensation: the money IS received, so the payment completes
            // and the webhook returns success (Stripe stops retrying). The
            // order is flagged for an operator: restock-and-continue, or
            // refund-and-cancel. Unconfirmed reservations are released;
            // already-confirmed ones keep their allocation (release() no-ops
            // on CONFIRMED).
            log.error("PAYMENT_RECEIVED_INVENTORY_CONFLICT | paymentId={} orderId={} reason={} — order flagged NEEDS_ATTENTION",
                    payment.getId(), order.getId(), ex.getMessage());
            reservationService.releaseForOrder(order.getId());
            order.markNeedsAttention();
        }

        payment.complete(reference);

        cartRepository.findByUserId(order.getUser().getId())
                .ifPresent(cart -> cart.getCartItems().clear());

        eventPublisher.publishEvent(
                new PaymentCompletedEvent(this, payment.getId(), order.getId())
        );
    }

    private void handleFailure(Payment payment) {

        Order order = payment.getOrder();

        payment.fail("Payment failed at " + Instant.now());

        reservationService.releaseForOrder(order.getId());
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