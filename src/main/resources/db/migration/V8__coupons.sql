-- Phase 7 (coupons): discount coupon engine. Codes are stored upper-cased
-- and unique; usage is counted on the coupon row inside the order transaction.
CREATE TABLE `coupons` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(50) NOT NULL,
    `type` VARCHAR(20) NOT NULL,
    `value` DECIMAL(12,2) NOT NULL,
    `min_subtotal` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    `max_discount` DECIMAL(12,2) NULL,
    `active` BIT(1) NOT NULL DEFAULT 1,
    `starts_at` DATETIME(6) NULL,
    `ends_at` DATETIME(6) NULL,
    `usage_limit` INT NULL,
    `used_count` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT 0,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `created_by` VARCHAR(100) NULL,
    `updated_by` VARCHAR(100) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_coupon_code` (`code`),
    INDEX `idx_coupon_active` (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
