-- Add PENDING_VERIFICATION to users status CHECK constraint
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;

ALTER TABLE users
    ADD CONSTRAINT users_status_check
        CHECK (status IN ('ACTIVE', 'LOCKED', 'INACTIVE', 'PENDING_VERIFICATION'));
