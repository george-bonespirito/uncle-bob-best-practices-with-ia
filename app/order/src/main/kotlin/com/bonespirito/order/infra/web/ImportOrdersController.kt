package com.bonespirito.order.infra.web

import com.bonespirito.order.application.ImportOrders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/imports/orders")
class ImportOrdersController(private val importOrders: ImportOrders) {

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@RequestParam("file") file: MultipartFile): ResponseEntity<ImportBatchResponse> {
        val fileName = file.originalFilename?.ifBlank { null } ?: file.name
        val accepted = importOrders.startImport(fileName, file.inputStream)
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ImportBatchResponse(accepted.importBatchId, accepted.status.name))
    }
}

data class ImportBatchResponse(val importBatchId: UUID, val status: String)
