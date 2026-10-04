package com.bonespirito.order.infra.persistence

import com.bonespirito.order.application.port.ImportBatchRepository
import com.bonespirito.order.domain.ImportBatch
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.util.UUID

/** Adapter JPA do [ImportBatchRepository]: o Spring Data fica aqui dentro, fora de `application`. */
@Repository
class JpaImportBatchRepository internal constructor(
    private val springData: SpringDataImportBatchRepository,
) : ImportBatchRepository {

    override fun save(importBatch: ImportBatch): ImportBatch = springData.save(importBatch)

    override fun findById(id: UUID): ImportBatch? = springData.findByIdOrNull(id)

    override fun findByUploadedFileFingerprint(fingerprint: String): ImportBatch? =
        springData.findByUploadedFileFingerprint(fingerprint)
}

internal interface SpringDataImportBatchRepository : JpaRepository<ImportBatch, UUID> {
    fun findByUploadedFileFingerprint(fingerprint: String): ImportBatch?
}
