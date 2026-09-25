-- Phase 4 (security hardening): vendor ownership for products.
--
-- Legacy-data strategy (deliberate, not accidental):
--   vendor_user_id IS NULL  -> "admin-managed" product. Every pre-V6 product
--   falls into this class because no historical vendor attribution exists
--   (auditor columns were never populated before this migration). NULL rows
--   stay fully manageable by ADMIN and are explicitly OFF-LIMITS to VENDOR
--   roles, so no legacy product becomes uneditable (admins retain control)
--   and none can be claimed or tampered with by an arbitrary vendor.
--   New products created by a VENDOR principal are stamped with that user's id
--   and can then only be mutated by the same vendor or an ADMIN.
ALTER TABLE products
    ADD COLUMN vendor_user_id BIGINT NULL AFTER sku;

CREATE INDEX idx_product_vendor_user ON products (vendor_user_id);
