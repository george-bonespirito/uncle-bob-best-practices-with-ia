package com.bonespirito.order.application

import com.bonespirito.order.application.port.ImportBatchRepository
import com.bonespirito.order.application.port.ImportBatchRowErrorRepository
import com.bonespirito.order.application.port.ImportFileParser
import com.bonespirito.order.application.port.OrderRepository
import com.bonespirito.order.domain.ImportBatch
import com.bonespirito.order.domain.ImportBatchRowError
import com.bonespirito.order.domain.ImportProgress
import com.bonespirito.order.domain.Order
import com.bonespirito.order.domain.OrderItem
import com.bonespirito.order.domain.SourceOrderReference
import com.bonespirito.order.domain.policy.GroupedOrder
import com.bonespirito.order.domain.policy.ParsedRow
import com.bonespirito.order.domain.policy.groupIntoOrders
import org.springframework.stereotype.Service
import java.io.InputStream
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

    override fun startImport(fileName: String, content: InputStream): ImportBatchAccepted {
        val bytes = content.readBytes()
        val rowCount = (bytes.inputStream().bufferedReader().readLines().size - 1).coerceAtLeast(0)
        val batch = importBatches.save(
            ImportBatch(fileName = fileName, progress = ImportProgress(totalRows = rowCount)),
        )

        executor.submit { process(batch.id, bytes) }

        return ImportBatchAccepted(batch.id, batch.status)
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
        val validRows = parsedRows.filterIsInstance<ParsedRow.Valid>().map { it.row }
        val invalidRows = parsedRows.filterIsInstance<ParsedRow.Invalid>()
        val grouped = groupIntoOrders(validRows)

        grouped.forEach { orders.save(it.toOrder()) }
        invalidRows.forEach { rowErrors.save(it.toRowError(importBatchId)) }

        val batch = importBatches.findById(importBatchId).orElseThrow()
        batch.progress = batch.progress.copy(
            processedRows = parsedRows.size,
            ordersCreated = grouped.size,
            rowErrorCount = invalidRows.size,
        )
        batch.finish(hasErrors = invalidRows.isNotEmpty())
        importBatches.save(batch)
    }

    private fun GroupedOrder.toOrder(): Order {
        val order = Order(
            sourceOrderReference = SourceOrderReference(sourceSystemCode, sourceOrderNumber),
            customerId = customerId,
            orderCreatedAt = orderCreatedAt,
        )
        items.forEach { row -> order.items.add(OrderItem(order = order, sku = row.sku, quantity = row.quantity)) }
        return order
    }

    private fun ParsedRow.Invalid.toRowError(importBatchId: UUID) =
        ImportBatchRowError(importBatchId = importBatchId, rowNumber = rowNumber, reason = reason)
}
