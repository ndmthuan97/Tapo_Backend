-- V4: Create contact_messages table
-- Stores contact form submissions for admin review

CREATE TABLE IF NOT EXISTS contact_messages (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    email      VARCHAR(200) NOT NULL,
    phone      VARCHAR(20),
    topic      VARCHAR(100) NOT NULL,
    message    TEXT         NOT NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_contact_messages_created_at ON contact_messages(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_contact_messages_is_read    ON contact_messages(is_read);
