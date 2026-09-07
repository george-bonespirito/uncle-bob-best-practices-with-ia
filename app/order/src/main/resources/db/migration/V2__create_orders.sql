CREATE TABLE orders (
    id                  UUID PRIMARY KEY,
    source_system_code  VARCHAR(50)  NOT NULL,
    source_order_number VARCHAR(100) NOT NULL,
    customer_id         UUID         NOT NULL,
    order_created_at    TIMESTAMP    NOT NULL,
    status              VARCHAR(30)  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (source_system_code, source_order_number)
);

CREATE TABLE order_items (
    id       UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    sku      VARCHAR(50) NOT NULL,
    quantity INTEGER NOT NULL
);
