-- Add cancel_reason to orders table
ALTER TABLE orders
ADD COLUMN cancel_reason VARCHAR(255);
