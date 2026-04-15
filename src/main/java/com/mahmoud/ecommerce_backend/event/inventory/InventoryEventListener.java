package com.mahmoud.ecommerce_backend.event.inventory;

import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.enums.StockMovementType;
import com.mahmoud.ecommerce_backend.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventListener {

    private final StockMovementRepository stockMovementRepository;

    @EventListener
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {

        Order order = event.getOrder();

        for (OrderItem item : order.getOrderItems()) {

            int quantity = item.getQuantity();

            StockMovement movement = StockMovement.builder()
                    .productId(item.getProductId())
                    .variantId(item.getVariantId())
                    .movementType(StockMovementType.ORDER_OUT)
                    .quantity(quantity)
                    .beforeQuantity(null)
                    .afterQuantity(null)
                    .referenceId(order.getId())
                    .referenceType("ORDER")
                    .note("Order #" + order.getOrderNumber())
                    .build();

            stockMovementRepository.save(movement);
        }

        log.info("Stock movements created | orderId={} itemsCount={}",
                order.getId(),
                order.getOrderItems().size());
    }
}