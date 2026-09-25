-- Orders that were paid at the gateway but whose stock could no longer be
-- confirmed (reservation expired and inventory went elsewhere) need an
-- explicit operator-visible state. See PaymentServiceImpl.handleSuccess.
ALTER TABLE orders
    MODIFY COLUMN `status` enum('PENDING','CONFIRMED','PAID','PROCESSING','SHIPPED','OUT_FOR_DELIVERY','DELIVERED','CANCELLED','REFUNDED','FAILED','NEEDS_ATTENTION') NOT NULL;
