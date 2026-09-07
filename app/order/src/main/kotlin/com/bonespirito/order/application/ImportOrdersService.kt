package com.bonespirito.order.application

import com.bonespirito.order.application.port.ImportBatchRepository
import com.bonespirito.order.application.port.ImportFileParser
import com.bonespirito.order.application.port.OrderRepository
import com.bonespirito.order.domain.ImportBatch
import com.bonespirito.order.domain.ImportProgress
import com.bonespirito.order.domain.Order
import com.bonespirito.order.domain.OrderItem
import com.bonespirito.order.domain.SourceOrderReference
import com.bonespirito.order.domain.policy.GroupedOrder
import com.bonespirito.order.domain.policy.groupIntoOrders
import org.springframework.stereotype.Service
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ExecutorService

@Service
class ImportOrdersService(
    private val importBatches: ImportBatchRepository,
    private val orders: OrderRepository,
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
            )
        }.orElse(null)

    private fun process(importBatchId: UUID, bytes: ByteArray) {
        val rows = parser.parse(bytes.inputStream())
        val grouped = groupIntoOrders(rows)

        grouped.forEach { orders.save(it.toOrder()) }

        val batch = importBatches.findById(importBatchId).orElseThrow()
        batch.progress = batch.progress.copy(processedRows = rows.size, ordersCreated = grouped.size)
        batch.markCompleted()
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
}
