package com.bonespirito.order.application.port

import com.bonespirito.order.domain.ImportBatch
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ImportBatchRepository : JpaRepository<ImportBatch, UUID> {
    fun findByUploadedFileFingerprint(fingerprint: String): ImportBatch?
}
