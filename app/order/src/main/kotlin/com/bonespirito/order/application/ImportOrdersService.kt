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
import com.bonespirito.order.domain.policy.mergeQuantitiesBySku
import com.bonespirito.order.domain.policy.partitionByDuplicate
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
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
    private val transaction: TransactionOperations,
) : ImportOrders, ImportBatchStatusQuery {

    override fun startImport(fileName: String, content: InputStream): ImportUploadResult {
        val bytes = content.readBytes()
        val fingerprint = fingerprintOf(bytes)
        return findDuplicateFile(fingerprint) ?: acceptNewBatch(fileName, fingerprint, bytes)
    }

    /**
     * Dois uploads simultâneos do mesmo arquivo passam juntos pela consulta de fingerprint;
     * o perdedor bate no `uq_import_batches_file_fingerprint` (V4). Se a releitura acha o lote
     * do vencedor, é a mesma recusa de reenvio (409); senão a violação é outra e sobe.
     */
    private fun acceptNewBatch(fileName: String, fingerprint: String, bytes: ByteArray): ImportUploadResult {
        val rowCount = (bytes.inputStream().bufferedReader().readLines().size - 1).coerceAtLeast(0)
        val batch = try {
            importBatches.save(
                ImportBatch(
                    uploadedFile = UploadedFile(fileName, fingerprint),
                    progress = ImportProgress(totalRows = rowCount),
                ),
            )
        } catch (e: DataIntegrityViolationException) {
            return findDuplicateFile(fingerprint) ?: throw e
        }

        executor.submit { process(batch.id, bytes) }

        return ImportUploadResult.Accepted(batch.id, batch.status)
    }

    private fun findDuplicateFile(fingerprint: String): ImportUploadResult.DuplicateFile? =
        importBatches.findByUploadedFileFingerprint(fingerprint)?.let {
            ImportUploadResult.DuplicateFile(it.id, it.status)
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

    /**
     * Roda no executor em background: exceção que escapasse daqui seria engolida pelo
     * `Future` descartado e o lote ficaria `PENDING` para sempre. Por isso esta é a
     * fronteira que captura qualquer falha, registra no log e encerra o lote em `FAILED`.
     *
     * Três transações, nesta ordem: `PROCESSING` (fica visível enquanto roda); Orders, erros
     * de linha e fechamento do lote numa só (`TransactionOperations` explícito — a chamada
     * vem de outra thread e de dentro da própria classe, onde `@Transactional` não passaria
     * pelo proxy); e, se essa falhar e voltar inteira, `FAILED` numa transação à parte.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun process(importBatchId: UUID, bytes: ByteArray) {
        try {
            updateBatch(importBatchId) { it.start() }
            transaction.executeWithoutResult { importRows(importBatchId, bytes) }
        } catch (e: Exception) {
            log.error("Import Batch {} falhou no processamento; marcando como FAILED", importBatchId, e)
            markFailed(importBatchId)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun markFailed(importBatchId: UUID) {
        try {
            updateBatch(importBatchId) { it.fail() }
        } catch (e: Exception) {
            log.error("Import Batch {} não pôde ser marcado como FAILED", importBatchId, e)
        }
    }

    private fun updateBatch(importBatchId: UUID, change: (ImportBatch) -> Unit) {
        val batch = importBatches.findById(importBatchId).orElseThrow()
        change(batch)
        importBatches.save(batch)
    }

    private fun importRows(importBatchId: UUID, bytes: ByteArray) {
        val parsedRows = parser.parse(bytes.inputStream())
        val validRows = parsedRows.filterIsInstance<ParsedRow.Valid>().map { it.numberedRow }
        val invalidRows = parsedRows.filterIsInstance<ParsedRow.Invalid>()

        val groupedOrders = groupIntoOrders(validRows)
        val partition = partitionByDuplicate(groupedOrders, existingKeysAmong(groupedOrders))

        partition.new.forEach { orders.save(it.toOrder()) }

        val rowErrorsToSave = invalidRows.map { it.toRowError(importBatchId) } +
            partition.duplicates.flatMap { it.toDuplicateRowErrors(importBatchId) }
        rowErrorsToSave.forEach { rowErrors.save(it) }

        updateBatch(importBatchId) { batch ->
            batch.progress = batch.progress.copy(
                processedRows = parsedRows.size,
                ordersCreated = partition.new.size,
                rowErrorCount = rowErrorsToSave.size,
            )
            batch.finish(hasErrors = rowErrorsToSave.isNotEmpty())
        }
    }

    /** Só as chaves do lote, em blocos abaixo do limite de parâmetros por statement do banco. */
    private fun existingKeysAmong(groupedOrders: List<GroupedOrder>): Set<Pair<String, String>> =
        groupedOrders.groupBy({ it.sourceSystemCode }, { it.sourceOrderNumber })
            .flatMap { (sourceSystemCode, sourceOrderNumbers) ->
                sourceOrderNumbers.chunked(KEY_LOOKUP_CHUNK_SIZE)
                    .flatMap { orders.findExistingSourceOrderNumbers(sourceSystemCode, it) }
                    .map { sourceSystemCode to it }
            }
            .toSet()

    private fun fingerprintOf(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun GroupedOrder.toOrder(): Order {
        val order = Order(
            sourceOrderReference = SourceOrderReference(sourceSystemCode, sourceOrderNumber),
            customerId = customerId,
            orderCreatedAt = orderCreatedAt,
        )
        mergeQuantitiesBySku(items).forEach { (sku, quantity) ->
            order.items.add(OrderItem(order = order, sku = sku, quantity = quantity))
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

    private companion object {
        const val KEY_LOOKUP_CHUNK_SIZE = 1_000
        val log = LoggerFactory.getLogger(ImportOrdersService::class.java)
    }
}
