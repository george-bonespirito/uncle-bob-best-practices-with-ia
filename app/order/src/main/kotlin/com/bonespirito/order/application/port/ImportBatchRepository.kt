package com.bonespirito.order.application.port

import com.bonespirito.order.domain.ImportBatch
import java.util.UUID

/** Port de persistência do Import Batch (ADR 0009). O adapter mora em `infra.persistence`. */
interface ImportBatchRepository {
    fun save(importBatch: ImportBatch): ImportBatch

    fun findById(id: UUID): ImportBatch?

    fun findByUploadedFileFingerprint(fingerprint: String): ImportBatch?
}
