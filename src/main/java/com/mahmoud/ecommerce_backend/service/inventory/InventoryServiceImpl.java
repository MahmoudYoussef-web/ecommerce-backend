package com.mahmoud.ecommerce_backend.service.inventory;

import com.mahmoud.ecommerce_backend.entity.Product;
import com.mahmoud.ecommerce_backend.entity.StockMovement;
import com.mahmoud.ecommerce_backend.enums.StockMovementType;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.repository.ProductRepository;
import com.mahmoud.ecommerce_backend.repository.StockMovementRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final EntityManager entityManager;

    private static final int MAX_RETRIES = 3;

    @Override
    @Transactional
    public void reserveStock(Long productId, Integer quantity) {
        validate(productId, quantity);
        executeWithRetry(productId, quantity, -quantity, StockMovementType.ORDER_OUT, "RESERVE");
    }

    @Override
    @Transactional
    public void releaseStock(Long productId, Integer quantity) {
        validate(productId, quantity);
        executeWithRetry(productId, quantity, quantity, StockMovementType.RETURN_IN, "RELEASE");
    }

    @Override
    @Transactional
    public void confirmStock(Long productId, Integer quantity) {
        validate(productId, quantity);

        Product product = findProduct(productId);

        saveMovement(
                productId,
                quantity,
                product.getStockQuantity(),
                product.getStockQuantity(),
                StockMovementType.ORDER_OUT,
                "CONFIRM"
        );
    }



    private void executeWithRetry(Long productId,
                                  int quantity,
                                  int delta,
                                  StockMovementType type,
                                  String note) {

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {

                Product product = findProductWithLock(productId);

                int before = product.getStockQuantity();
                int after = before + delta;

                if (after < 0) {
                    throw new BadRequestException("Insufficient stock");
                }

                product.setStockQuantity(after);

                saveMovement(productId, Math.abs(quantity), before, after, type, note);

                return;

            } catch (OptimisticLockingFailureException ex) {

                log.warn("Stock update retry productId={}, attempt={}", productId, attempt);

                if (attempt == MAX_RETRIES) {
                    throw new BadRequestException("Stock update failed due to concurrency");
                }
            }
        }
    }



    private Product findProductWithLock(Long productId) {
        Product product = findProduct(productId);
        entityManager.lock(product, LockModeType.PESSIMISTIC_WRITE);
        return product;
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private void validate(Long productId, Integer quantity) {
        if (productId == null) {
            throw new BadRequestException("ProductId required");
        }
        if (quantity == null || quantity <= 0) {
            throw new BadRequestException("Invalid quantity");
        }
    }

    private void saveMovement(Long productId,
                              int quantity,
                              int before,
                              int after,
                              StockMovementType type,
                              String note) {

        stockMovementRepository.save(
                StockMovement.builder()
                        .productId(productId)
                        .quantity(quantity)
                        .beforeQuantity(before)
                        .afterQuantity(after)
                        .movementType(type)
                        .referenceType(note)
                        .build()
        );
    }
}