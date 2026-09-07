CREATE TABLE import_batches (
    id              UUID PRIMARY KEY,
    file_name       VARCHAR(255) NOT NULL,
    status          VARCHAR(30)  NOT NULL,
    total_rows      INTEGER,
    processed_rows  INTEGER      NOT NULL DEFAULT 0,
    orders_created  INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL,
    completed_at    TIMESTAMP
);
