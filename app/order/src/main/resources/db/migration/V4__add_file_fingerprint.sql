ALTER TABLE import_batches ADD COLUMN file_fingerprint VARCHAR(64);
UPDATE import_batches SET file_fingerprint = CAST(id AS VARCHAR) WHERE file_fingerprint IS NULL;
ALTER TABLE import_batches ALTER COLUMN file_fingerprint SET NOT NULL;
ALTER TABLE import_batches ADD CONSTRAINT uq_import_batches_file_fingerprint UNIQUE (file_fingerprint);
