package com.bonespirito.order.application

import com.bonespirito.order.domain.ImportBatchStatus
import java.io.InputStream
import java.util.UUID

interface ImportOrders {
    fun startImport(fileName: String, content: InputStream): ImportUploadResult
}

sealed interface ImportUploadResult {
    data class Accepted(val importBatchId: UUID, val status: ImportBatchStatus) : ImportUploadResult
    data class DuplicateFile(
        val existingImportBatchId: UUID,
        val existingStatus: ImportBatchStatus,
    ) : ImportUploadResult
}
