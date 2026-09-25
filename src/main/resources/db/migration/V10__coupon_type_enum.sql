-- Phase 7 fix: coupon type follows the codebase convention of native
-- MySQL ENUMs (see V1 baseline), which Hibernate validates against.
ALTER TABLE `coupons`
    MODIFY COLUMN `type` enum('PERCENT','FIXED') NOT NULL;
