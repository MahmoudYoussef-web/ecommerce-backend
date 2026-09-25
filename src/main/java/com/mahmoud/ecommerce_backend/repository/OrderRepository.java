package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    List<Order> findByUserId(Long userId);

    /** DB-level pagination for customer order history. */
    Page<Order> findByUserId(Long userId, Pageable pageable);

    /** Batched eager user load for page IDs — one query per admin listing page. */
    @Query("select o from Order o join fetch o.user where o.id in :ids")
    List<Order> findAllWithUserByIdIn(@Param("ids") List<Long> ids);

    List<Order> findAllByOrderByCreatedAtDesc();

    Optional<Order> findByOrderNumber(String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
}
