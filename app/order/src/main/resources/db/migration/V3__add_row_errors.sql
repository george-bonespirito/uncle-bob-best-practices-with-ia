ALTER TABLE import_batches ADD COLUMN row_error_count INTEGER NOT NULL DEFAULT 0;

CREATE TABLE import_batch_row_errors (
    id              UUID PRIMARY KEY,
    import_batch_id UUID NOT NULL REFERENCES import_batches(id),
    row_number      INTEGER NOT NULL,
    reason          VARCHAR(255) NOT NULL
);
