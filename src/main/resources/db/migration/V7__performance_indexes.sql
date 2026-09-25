-- Phase 6 (performance): composite indexes for the hottest listing/report paths.
-- Each index is justified by captured EXPLAIN evidence:
--   products(status, created_at): default catalog listing sorts by created_at
--     within status='ACTIVE' — previously "Using filesort" over the status index.
--   orders(status, created_at): admin orders default listing filters by status
--     and sorts by created_at desc — same filesort pattern at scale.
--   payments(status, paid_at): dashboard/revenue range aggregation filtered by
--     COMPLETED + paid_at BETWEEN — previously only a bare status index existed.
CREATE INDEX idx_product_status_created ON products (status, created_at);
CREATE INDEX idx_order_status_created ON orders (status, created_at);
CREATE INDEX idx_payment_status_paid_at ON payments (status, paid_at);
