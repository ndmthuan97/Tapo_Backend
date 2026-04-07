-- V5: Create product_images table for multi-image support
-- Entity ProductImage already exists in code (JPA will not auto-create since ddl-auto=validate in prod)

CREATE TABLE IF NOT EXISTS product_images (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    product_id  UUID        NOT NULL,
    image_url   VARCHAR(500) NOT NULL,
    alt_text    VARCHAR(255),
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_product_images_product
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_product_images_product_id ON product_images(product_id);
CREATE INDEX IF NOT EXISTS idx_product_images_sort      ON product_images(product_id, sort_order);
