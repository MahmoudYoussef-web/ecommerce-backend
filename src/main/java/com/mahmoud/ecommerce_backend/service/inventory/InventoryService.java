package com.mahmoud.ecommerce_backend.service.inventory;

public interface InventoryService {

    void reserveStock(Long productId, Integer quantity);

    void releaseStock(Long productId, Integer quantity);

    void confirmStock(Long productId, Integer quantity);
}