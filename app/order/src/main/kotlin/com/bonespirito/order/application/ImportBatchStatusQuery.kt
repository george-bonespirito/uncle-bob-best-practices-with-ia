package com.bonespirito.order.application

import com.bonespirito.order.domain.ImportBatchStatus
import java.util.UUID

interface ImportBatchStatusQuery {
    fun findStatus(importBatchId: UUID): ImportBatchStatusView?
}

data class ImportBatchStatusView(
    val importBatchId: UUID,
    val status: ImportBatchStatus,
    val totalRows: Int?,
    val processedRows: Int,
    val ordersCreated: Int,
)
