package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.entity.Payment;
import com.mahmoud.ecommerce_backend.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.order.id = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@org.springframework.data.repository.query.Param("orderId") Long orderId);

    Optional<Payment> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(Long id);

    /** Fetch the payment with its linked order initialized so detached reads of
     *  order status/id do not trigger a lazy-initialization failure. Used by the
     *  payment provider to build a Stripe Checkout session outside any transaction. */
    @Query("select p from Payment p join fetch p.order where p.id = :id")
    Optional<Payment> findByIdWithOrder(@org.springframework.data.repository.query.Param("id") Long id);

    List<Payment> findByStatusAndPaidAtBetween(
            PaymentStatus status,
            Instant from,
            Instant to
    );

    /** Grouped payment-status lookup for listing pages — avoids per-row lazy inverse access. */
    @Query("select p.order.id, p.status from Payment p where p.order.id in :orderIds")
    List<Object[]> findStatusByOrderIds(@org.springframework.data.repository.query.Param("orderIds") List<Long> orderIds);

    /** Aggregate revenue/orders for reports — no entity hydration. */
    @Query("select coalesce(sum(p.amount), 0), count(p) from Payment p " +
            "where p.status = :status and p.paidAt between :from and :to")
    List<Object[]> aggregateRevenue(@Param("status") PaymentStatus status,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to);
}