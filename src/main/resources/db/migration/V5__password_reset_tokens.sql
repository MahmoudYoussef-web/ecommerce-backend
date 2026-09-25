-- Phase 4 (security hardening): single-use, short-lived password reset tokens.
-- Only the SHA-256 hash of the token is persisted; the raw token exists solely
-- inside the reset link emailed to the user.
CREATE TABLE `password_reset_tokens` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `token_hash` VARCHAR(64) NOT NULL,
    `expires_at` DATETIME(6) NOT NULL,
    `consumed` BIT(1) NOT NULL DEFAULT 0,
    `consumed_at` DATETIME(6) NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT 0,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `created_by` VARCHAR(100) NULL,
    `updated_by` VARCHAR(100) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_prt_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    INDEX `idx_prt_token_hash` (`token_hash`),
    INDEX `idx_prt_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
