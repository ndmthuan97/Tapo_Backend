-- V3: Add payment_method column to orders table
-- Required by: Order entity field paymentMethod added in sprint 3

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(20) NOT NULL DEFAULT 'COD';
