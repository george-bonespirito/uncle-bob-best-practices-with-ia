package com.bonespirito.order.application

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.bonespirito.order.application.port.ImportBatchRepository
import com.bonespirito.order.application.port.ImportBatchRowErrorRepository
import com.bonespirito.order.application.port.ImportFileParser
import com.bonespirito.order.application.port.OrderRepository
import com.bonespirito.order.domain.ImportBatch
import com.bonespirito.order.domain.ImportBatchRowError
import com.bonespirito.order.domain.ImportBatchStatus
import com.bonespirito.order.domain.ImportRow
import com.bonespirito.order.domain.NumberedRow
import com.bonespirito.order.domain.Order
import com.bonespirito.order.domain.SourceOrderReference
import com.bonespirito.order.domain.UploadedFile
import com.bonespirito.order.domain.policy.ParsedRow
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ImportOrdersServiceTest {

    private val importBatches = mockk<ImportBatchRepository>()
    private val orders = mockk<OrderRepository>()
    private val rowErrors = mockk<ImportBatchRowErrorRepository>()
    private val parser = mockk<ImportFileParser>()
    private val executor = Executors.newSingleThreadExecutor()
    private val service = ImportOrdersService(importBatches, orders, rowErrors, parser, executor)

    private val serviceLogger = LoggerFactory.getLogger(ImportOrdersService::class.java) as Logger
    private val logs = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun capturaLogs() {
        logs.start()
        serviceLogger.addAppender(logs)
    }

    @AfterEach
    fun soltaLogs() {
        serviceLogger.detachAppender(logs)
    }

    @Test
    fun `aceita o upload, persiste o batch como PENDING com a contagem de linhas e devolve o id`() {
        val saved = slot<ImportBatch>()
        every { importBatches.findByUploadedFileFingerprint(any()) } returns null
        every { importBatches.save(capture(saved)) } answers { saved.captured }
        every { importBatches.findById(any()) } returns Optional.empty()
        every { orders.findAll() } returns emptyList()
        every { parser.parse(any()) } returns emptyList()

        val threeDataRows = "header\nrow1\nrow2\nrow3".byteInputStream()

        val accepted = service.startImport("orders.csv", threeDataRows) as ImportUploadResult.Accepted

        assertThat(accepted.status).isEqualTo(ImportBatchStatus.PENDING)
        assertThat(saved.captured.uploadedFile.fileName).isEqualTo("orders.csv")
        assertThat(saved.captured.progress.totalRows).isEqualTo(3)
    }

    @Test
    fun `reenvio do mesmo arquivo devolve DuplicateFile com o batch original, sem criar um novo`() {
        val original = ImportBatch(uploadedFile = UploadedFile("orders.csv", "qualquer-fingerprint"))
        every { importBatches.findByUploadedFileFingerprint(any()) } returns original

        val result = service.startImport("orders.csv", "header\nrow1".byteInputStream())

        assertThat(result).isEqualTo(ImportUploadResult.DuplicateFile(original.id, original.status))
        verify(exactly = 0) { importBatches.save(any()) }
    }

    @Test
    fun `processa em background, cria um Order por pedido agrupado e marca o batch como COMPLETED`() {
        val batch = ImportBatch(uploadedFile = UploadedFile("orders.csv", "fp-1"))
        every { importBatches.findByUploadedFileFingerprint(any()) } returns null
        every { importBatches.save(any()) } returns batch
        every { importBatches.findById(batch.id) } returns Optional.of(batch)
        every { orders.findAll() } returns emptyList()
        every { orders.save(any()) } answers { firstArg() }
        every { parser.parse(any()) } returns listOf(
            ParsedRow.Valid(numberedRow(1, "PO-001", "SKU-1", 2)),
            ParsedRow.Valid(numberedRow(2, "PO-002", "SKU-2", 1)),
        )

        service.startImport("orders.csv", "header\nrow1\nrow2".byteInputStream())
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.SECONDS)

        assertThat(batch.status).isEqualTo(ImportBatchStatus.COMPLETED)
        assertThat(batch.completedAt).isNotNull()
        assertThat(batch.progress.ordersCreated).isEqualTo(2)
        verify(exactly = 2) { orders.save(any<Order>()) }
        verify { importBatches.save(batch) }
    }

    @Test
    fun `linha invalida nao vira Order, gera erro e marca o batch como COMPLETED_WITH_ERRORS`() {
        val batch = ImportBatch(uploadedFile = UploadedFile("orders.csv", "fp-2"))
        every { importBatches.findByUploadedFileFingerprint(any()) } returns null
        every { importBatches.save(any()) } returns batch
        every { importBatches.findById(batch.id) } returns Optional.of(batch)
        every { orders.findAll() } returns emptyList()
        every { orders.save(any()) } answers { firstArg() }
        every { rowErrors.save(any()) } answers { firstArg() }
        every { parser.parse(any()) } returns listOf(
            ParsedRow.Valid(numberedRow(1, "PO-001", "SKU-1", 2)),
            ParsedRow.Invalid(rowNumber = 2, reason = "customer_id ausente"),
        )

        service.startImport("orders.csv", "header\nrow1\nrow2".byteInputStream())
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.SECONDS)

        assertThat(batch.status).isEqualTo(ImportBatchStatus.COMPLETED_WITH_ERRORS)
        assertThat(batch.progress.ordersCreated).isEqualTo(1)
        assertThat(batch.progress.rowErrorCount).isEqualTo(1)
        verify(exactly = 1) { orders.save(any<Order>()) }
        verify {
            rowErrors.save(match<ImportBatchRowError> { it.reason == "customer_id ausente" && it.rowNumber == 2 })
        }
    }

    @Test
    fun `pedido com source_order_number ja existente em outro lote vira erro de duplicata, sem criar Order`() {
        val batch = ImportBatch(uploadedFile = UploadedFile("orders.csv", "fp-3"))
        val existingOrder = mockk<Order>()
        every { existingOrder.sourceOrderReference } returns SourceOrderReference("ERP-SAP", "PO-123")
        every { importBatches.findByUploadedFileFingerprint(any()) } returns null
        every { importBatches.save(any()) } returns batch
        every { importBatches.findById(batch.id) } returns Optional.of(batch)
        every { orders.findAll() } returns listOf(existingOrder)
        every { rowErrors.save(any()) } answers { firstArg() }
        every { parser.parse(any()) } returns listOf(ParsedRow.Valid(numberedRow(1, "PO-123", "SKU-9", 3)))

        service.startImport("orders.csv", "header\nrow1".byteInputStream())
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.SECONDS)

        assertThat(batch.status).isEqualTo(ImportBatchStatus.COMPLETED_WITH_ERRORS)
        assertThat(batch.progress.ordersCreated).isEqualTo(0)
        verify(exactly = 0) { orders.save(any()) }
        verify {
            rowErrors.save(match<ImportBatchRowError> { it.reason.contains("duplicata") && it.rowNumber == 1 })
        }
    }

    @Test
    fun `mesmo SKU no mesmo pedido vira um Order Item com a quantidade somada`() {
        val batch = ImportBatch(uploadedFile = UploadedFile("orders.csv", "fp-4"))
        val saved = slot<Order>()
        every { importBatches.findByUploadedFileFingerprint(any()) } returns null
        every { importBatches.save(any()) } returns batch
        every { importBatches.findById(batch.id) } returns Optional.of(batch)
        every { orders.findAll() } returns emptyList()
        every { orders.save(capture(saved)) } answers { saved.captured }
        every { parser.parse(any()) } returns listOf(
            ParsedRow.Valid(numberedRow(1, "PO-001", "SKU-1", 2)),
            ParsedRow.Valid(numberedRow(2, "PO-001", "SKU-1", 3)),
        )

        service.startImport("orders.csv", "header\nrow1\nrow2".byteInputStream())
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.SECONDS)

        assertThat(saved.captured.items).hasSize(1)
        assertThat(saved.captured.items.single().sku).isEqualTo("SKU-1")
        assertThat(saved.captured.items.single().quantity).isEqualTo(5)
    }

    @Test
    fun `ao iniciar o processamento o batch passa a PROCESSING antes de ler o arquivo`() {
        val batch = ImportBatch(uploadedFile = UploadedFile("orders.csv", "fp-5"))
        val statusesSalvos = mutableListOf<ImportBatchStatus>()
        var statusDuranteOParse: ImportBatchStatus? = null
        every { importBatches.findByUploadedFileFingerprint(any()) } returns null
        every { importBatches.save(any()) } answers {
            statusesSalvos += firstArg<ImportBatch>().status
            batch
        }
        every { importBatches.findById(batch.id) } returns Optional.of(batch)
        every { orders.findAll() } returns emptyList()
        every { parser.parse(any()) } answers {
            statusDuranteOParse = batch.status
            emptyList()
        }

        service.startImport("orders.csv", "header".byteInputStream())
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.SECONDS)

        assertThat(statusDuranteOParse).isEqualTo(ImportBatchStatus.PROCESSING)
        assertThat(statusesSalvos).containsExactly(
            ImportBatchStatus.PENDING,
            ImportBatchStatus.PROCESSING,
            ImportBatchStatus.COMPLETED,
        )
    }

    @Test
    fun `falha inesperada no processamento marca o batch como FAILED e registra o erro no log`() {
        val batch = ImportBatch(uploadedFile = UploadedFile("orders.csv", "fp-6"))
        val falha = IllegalStateException("banco fora do ar")
        every { importBatches.findByUploadedFileFingerprint(any()) } returns null
        every { importBatches.save(any()) } returns batch
        every { importBatches.findById(batch.id) } returns Optional.of(batch)
        every { parser.parse(any()) } throws falha

        service.startImport("orders.csv", "header\nrow1".byteInputStream())
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.SECONDS)

        assertThat(batch.status).isEqualTo(ImportBatchStatus.FAILED)
        assertThat(batch.completedAt).isNotNull()
        verify { importBatches.save(match { it.id == batch.id && it.status == ImportBatchStatus.FAILED }) }
        val erro = logs.list.single { it.level == Level.ERROR }
        assertThat(erro.formattedMessage).contains(batch.id.toString())
        assertThat(erro.throwableProxy.message).isEqualTo("banco fora do ar")
    }

    @Test
    fun `se nem marcar FAILED for possivel, registra as duas falhas no log sem deixar a excecao escapar`() {
        val batch = ImportBatch(uploadedFile = UploadedFile("orders.csv", "fp-7"))
        every { importBatches.findByUploadedFileFingerprint(any()) } returns null
        every { importBatches.save(any()) } returns batch
        every { importBatches.findById(batch.id) } throws IllegalStateException("banco fora do ar")

        service.startImport("orders.csv", "header\nrow1".byteInputStream())
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.SECONDS)

        val erros = logs.list.filter { it.level == Level.ERROR }
        assertThat(erros).hasSize(2)
        assertThat(erros).allSatisfy { assertThat(it.formattedMessage).contains(batch.id.toString()) }
    }

    @Test
    fun `findStatus devolve null quando o batch nao existe`() {
        every { importBatches.findById(any()) } returns Optional.empty()

        assertThat(service.findStatus(UUID.randomUUID())).isNull()
    }

    private fun numberedRow(rowNumber: Int, sourceOrderNumber: String, sku: String, quantity: Int) = NumberedRow(
        rowNumber,
        ImportRow("ERP-SAP", sourceOrderNumber, UUID.randomUUID(), Instant.now(), sku, quantity),
    )
}
