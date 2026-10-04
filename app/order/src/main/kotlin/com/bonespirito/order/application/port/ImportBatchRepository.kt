package com.bonespirito.order.application.port

import com.bonespirito.order.domain.ImportBatch
import java.util.UUID

/** Port de persistência do Import Batch (ADR 0009). O adapter mora em `infra.persistence`. */
interface ImportBatchRepository {
    fun save(importBatch: ImportBatch): ImportBatch

    fun findById(id: UUID): ImportBatch?

    /**
     * O lote que bloqueia o reenvio do arquivo com este Import Fingerprint (qualquer status
     * menos `FAILED`), ou `null` se nenhum bloqueia. Lote `FAILED` não bloqueia (ADR order/0004,
     * revisão 2026-10-04).
     */
    fun findBlockingByFingerprint(fingerprint: String): ImportBatch?
}
