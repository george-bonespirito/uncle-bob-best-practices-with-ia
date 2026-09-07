package com.bonespirito.order.domain

import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class ImportBatchStatus { PENDING, PROCESSING, COMPLETED, COMPLETED_WITH_ERRORS }

@Embeddable
data class ImportProgress(
    val totalRows: Int? = null,
    val processedRows: Int = 0,
    val ordersCreated: Int = 0,
)

@Entity
@Table(name = "import_batches")
class ImportBatch(
    @Id
    val id: UUID = UUID.randomUUID(),

    val fileName: String,

    @Enumerated(EnumType.STRING)
    var status: ImportBatchStatus = ImportBatchStatus.PENDING,

    var progress: ImportProgress = ImportProgress(),

    val createdAt: Instant = Instant.now(),
    var completedAt: Instant? = null,
) {
    fun markCompleted() {
        status = ImportBatchStatus.COMPLETED
        completedAt = Instant.now()
    }
}
