-- ADR order/0004, revisão 2026-10-04: arquivo cujo lote terminou FAILED pode ser reenviado.
-- active_fingerprint = file_fingerprint enquanto o lote bloqueia reenvio; NULL no FAILED.
-- NULL não colide em UNIQUE (H2 e PostgreSQL): vários FAILED por arquivo, no máximo um ativo.
ALTER TABLE import_batches ADD COLUMN active_fingerprint VARCHAR(64);
UPDATE import_batches SET active_fingerprint = file_fingerprint WHERE status <> 'FAILED';
ALTER TABLE import_batches ADD CONSTRAINT uq_import_batches_active_fingerprint UNIQUE (active_fingerprint);
-- IS NOT NULL explícito: sem ele, NULL = file_fingerprint dá unknown e o CHECK deixaria passar
-- um lote PENDING com active_fingerprint nulo, que não bloquearia nada.
ALTER TABLE import_batches ADD CONSTRAINT ck_import_batches_active_fingerprint CHECK (
    (status = 'FAILED' AND active_fingerprint IS NULL)
    OR (status <> 'FAILED' AND active_fingerprint IS NOT NULL AND active_fingerprint = file_fingerprint)
);
ALTER TABLE import_batches DROP CONSTRAINT uq_import_batches_file_fingerprint;
