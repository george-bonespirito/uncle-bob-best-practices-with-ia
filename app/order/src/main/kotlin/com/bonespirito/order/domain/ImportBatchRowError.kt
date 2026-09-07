package com.bonespirito.order.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "import_batch_row_errors")
class ImportBatchRowError(
    @Id
    val id: UUID = UUID.randomUUID(),
    val importBatchId: UUID,
    val rowNumber: Int,
    val reason: String,
)
