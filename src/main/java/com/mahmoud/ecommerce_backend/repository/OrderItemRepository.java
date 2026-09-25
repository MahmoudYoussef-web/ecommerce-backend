package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("select i.order.id, count(i) from OrderItem i where i.order.id in :orderIds group by i.order.id")
    List<Object[]> countByOrderIds(@Param("orderIds") List<Long> orderIds);

    /** Batched item fetch for order-history pages (denormalized columns — no product joins needed). */
    List<OrderItem> findByOrderIdIn(List<Long> orderIds);
}
