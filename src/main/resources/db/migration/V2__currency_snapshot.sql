-- Currency snapshot for reconciliation:
-- the EGP display price shown to the customer and the actual USD amount
-- charged via Stripe must always be derivable from the stored record.
ALTER TABLE orders
    ADD COLUMN total_amount_egp DECIMAL(12,2) NULL AFTER total_amount,
    ADD COLUMN exchange_rate DECIMAL(12,6) NULL AFTER total_amount_egp,
    ADD COLUMN exchange_rate_at DATETIME(6) NULL AFTER exchange_rate;
