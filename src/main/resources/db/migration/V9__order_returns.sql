-- Phase 7 (returns): customer return requests on delivered orders.
ALTER TABLE orders
    ADD COLUMN return_requested BIT(1) NOT NULL DEFAULT 0,
    ADD COLUMN return_reason VARCHAR(500) NULL,
    ADD COLUMN returned_at DATETIME(6) NULL;
