package com.bonespirito.order.application

import com.bonespirito.order.domain.ImportBatchStatus
import java.io.InputStream
import java.util.UUID

interface ImportOrders {
    fun startImport(fileName: String, content: InputStream): ImportBatchAccepted
}

data class ImportBatchAccepted(val importBatchId: UUID, val status: ImportBatchStatus)
