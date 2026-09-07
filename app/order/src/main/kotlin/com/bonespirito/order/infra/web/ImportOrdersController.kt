package com.bonespirito.order.infra.web

import com.bonespirito.order.application.ImportBatchStatusQuery
import com.bonespirito.order.application.ImportOrders
import com.bonespirito.order.application.ImportUploadResult
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/imports/orders")
class ImportOrdersController(
    private val importOrders: ImportOrders,
    private val importBatchStatus: ImportBatchStatusQuery,
) {

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@RequestParam("file") file: MultipartFile): ResponseEntity<ImportBatchResponse> {
        val fileName = file.originalFilename?.ifBlank { null } ?: file.name
        return when (val result = importOrders.startImport(fileName, file.inputStream)) {
            is ImportUploadResult.Accepted -> ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ImportBatchResponse(result.importBatchId, result.status.name))
            is ImportUploadResult.DuplicateFile -> ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ImportBatchResponse(result.existingImportBatchId, result.existingStatus.name))
        }
    }

    @GetMapping("/{importBatchId}")
    fun status(@PathVariable importBatchId: UUID): ResponseEntity<ImportBatchStatusResponse> {
        val view = importBatchStatus.findStatus(importBatchId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            ImportBatchStatusResponse(
                importBatchId = view.importBatchId,
                status = view.status.name,
                totalRows = view.totalRows,
                processedRows = view.processedRows,
                ordersCreated = view.ordersCreated,
                rowErrorCount = view.rowErrorCount,
            ),
        )
    }
}

data class ImportBatchResponse(val importBatchId: UUID, val status: String)

data class ImportBatchStatusResponse(
    val importBatchId: UUID,
    val status: String,
    val totalRows: Int?,
    val processedRows: Int,
    val ordersCreated: Int,
    val rowErrorCount: Int,
)
