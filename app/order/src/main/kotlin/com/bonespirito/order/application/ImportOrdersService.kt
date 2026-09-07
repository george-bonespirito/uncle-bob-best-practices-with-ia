package com.bonespirito.order.application

import com.bonespirito.order.application.port.ImportBatchRepository
import com.bonespirito.order.application.port.ImportBatchRowErrorRepository
import com.bonespirito.order.application.port.ImportFileParser
import com.bonespirito.order.application.port.OrderRepository
import com.bonespirito.order.domain.ImportBatch
import com.bonespirito.order.domain.ImportBatchRowError
import com.bonespirito.order.domain.ImportProgress
import com.bonespirito.order.domain.NumberedRow
import com.bonespirito.order.domain.Order
import com.bonespirito.order.domain.OrderItem
import com.bonespirito.order.domain.SourceOrderReference
import com.bonespirito.order.domain.UploadedFile
import com.bonespirito.order.domain.policy.GroupedOrder
import com.bonespirito.order.domain.policy.ParsedRow
import com.bonespirito.order.domain.policy.groupIntoOrders
import com.bonespirito.order.domain.policy.partitionByDuplicate
import org.springframework.stereotype.Service
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ExecutorService

@Service
class ImportOrdersService(
    private val importBatches: ImportBatchRepository,
    private val orders: OrderRepository,
    private val rowErrors: ImportBatchRowErrorRepository,
    private val parser: ImportFileParser,
    private val executor: ExecutorService,
) : ImportOrders, ImportBatchStatusQuery {

    override fun startImport(fileName: String, content: InputStream): ImportUploadResult {
        val bytes = content.readBytes()
        val fingerprint = fingerprintOf(bytes)

        importBatches.findByUploadedFileFingerprint(fingerprint)?.let {
            return ImportUploadResult.DuplicateFile(it.id, it.status)
        }

        val rowCount = (bytes.inputStream().bufferedReader().readLines().size - 1).coerceAtLeast(0)
        val batch = importBatches.save(
            ImportBatch(
                uploadedFile = UploadedFile(fileName, fingerprint),
                progress = ImportProgress(totalRows = rowCount),
            ),
        )

        executor.submit { process(batch.id, bytes) }

        return ImportUploadResult.Accepted(batch.id, batch.status)
    }

    override fun findStatus(importBatchId: UUID): ImportBatchStatusView? =
        importBatches.findById(importBatchId).map {
            ImportBatchStatusView(
                importBatchId = it.id,
                status = it.status,
                totalRows = it.progress.totalRows,
                processedRows = it.progress.processedRows,
                ordersCreated = it.progress.ordersCreated,
                rowErrorCount = it.progress.rowErrorCount,
            )
        }.orElse(null)

    private fun process(importBatchId: UUID, bytes: ByteArray) {
        val parsedRows = parser.parse(bytes.inputStream())
        val validRows = parsedRows.filterIsInstance<ParsedRow.Valid>().map { it.numberedRow }
        val invalidRows = parsedRows.filterIsInstance<ParsedRow.Invalid>()

        val existingKeys = orders.findAll()
            .map { it.sourceOrderReference.sourceSystemCode to it.sourceOrderReference.sourceOrderNumber }
            .toSet()
        val partition = partitionByDuplicate(groupIntoOrders(validRows), existingKeys)

        partition.new.forEach { orders.save(it.toOrder()) }

        val rowErrorsToSave = invalidRows.map { it.toRowError(importBatchId) } +
            partition.duplicates.flatMap { it.toDuplicateRowErrors(importBatchId) }
        rowErrorsToSave.forEach { rowErrors.save(it) }

        val batch = importBatches.findById(importBatchId).orElseThrow()
        batch.progress = batch.progress.copy(
            processedRows = parsedRows.size,
            ordersCreated = partition.new.size,
            rowErrorCount = rowErrorsToSave.size,
        )
        batch.finish(hasErrors = rowErrorsToSave.isNotEmpty())
        importBatches.save(batch)
    }

    private fun fingerprintOf(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun GroupedOrder.toOrder(): Order {
        val order = Order(
            sourceOrderReference = SourceOrderReference(sourceSystemCode, sourceOrderNumber),
            customerId = customerId,
            orderCreatedAt = orderCreatedAt,
        )
        items.forEach { item ->
            order.items.add(OrderItem(order = order, sku = item.row.sku, quantity = item.row.quantity))
        }
        return order
    }

    private fun GroupedOrder.toDuplicateRowErrors(importBatchId: UUID): List<ImportBatchRowError> =
        items.map { item -> item.toDuplicateRowError(importBatchId) }

    private fun NumberedRow.toDuplicateRowError(importBatchId: UUID) = ImportBatchRowError(
        importBatchId = importBatchId,
        rowNumber = rowNumber,
        reason = "duplicata de source_order_number",
    )

    private fun ParsedRow.Invalid.toRowError(importBatchId: UUID) =
        ImportBatchRowError(importBatchId = importBatchId, rowNumber = rowNumber, reason = reason)
}
