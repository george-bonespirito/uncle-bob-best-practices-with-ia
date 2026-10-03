package com.bonespirito.order.domain

import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class ImportBatchStatus { PENDING, PROCESSING, COMPLETED, COMPLETED_WITH_ERRORS, FAILED }

@Embeddable
data class ImportProgress(
    val totalRows: Int? = null,
    val processedRows: Int = 0,
    val ordersCreated: Int = 0,
    val rowErrorCount: Int = 0,
)

@Embeddable
data class UploadedFile(val fileName: String, val fingerprint: String)

@Entity
@Table(name = "import_batches")
class ImportBatch(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Embedded
    @AttributeOverride(name = "fingerprint", column = Column(name = "file_fingerprint"))
    val uploadedFile: UploadedFile,

    @Enumerated(EnumType.STRING)
    var status: ImportBatchStatus = ImportBatchStatus.PENDING,

    var progress: ImportProgress = ImportProgress(),

    val createdAt: Instant = Instant.now(),
    var completedAt: Instant? = null,
) {
    fun start() {
        status = ImportBatchStatus.PROCESSING
    }

    fun finish(hasErrors: Boolean) {
        status = if (hasErrors) ImportBatchStatus.COMPLETED_WITH_ERRORS else ImportBatchStatus.COMPLETED
        completedAt = Instant.now()
    }

    fun fail() {
        status = ImportBatchStatus.FAILED
        completedAt = Instant.now()
    }
}
