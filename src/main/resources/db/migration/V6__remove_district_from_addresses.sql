-- V6: Remove district column from addresses table
-- Reason: Vietnam abolished district-level (quận/huyện) administrative units (2025 reform)
-- New address structure: recipientName | phoneNumber | address (street + ward) | city (tỉnh/TP)

-- Step 1: Merge district into address field for existing records (avoid data loss)
UPDATE addresses
SET address = CONCAT(address, ', ', district)
WHERE district IS NOT NULL AND district <> '';

-- Step 2: Drop district column from addresses
ALTER TABLE addresses
DROP COLUMN district;

-- Step 3: Make orders.shipping_district nullable (backward compat — old orders keep district snapshot)
ALTER TABLE orders
MODIFY COLUMN shipping_district VARCHAR(100) NULL;
