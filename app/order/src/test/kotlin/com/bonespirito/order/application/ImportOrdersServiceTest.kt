package com.bonespirito.order.application

import com.bonespirito.order.application.port.ImportBatchRepository
import com.bonespirito.order.application.port.ImportFileParser
import com.bonespirito.order.application.port.OrderRepository
import com.bonespirito.order.domain.ImportBatch
import com.bonespirito.order.domain.ImportBatchStatus
import com.bonespirito.order.domain.ImportRow
import com.bonespirito.order.domain.Order
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ImportOrdersServiceTest {

    private val importBatches = mockk<ImportBatchRepository>()
    private val orders = mockk<OrderRepository>()
    private val parser = mockk<ImportFileParser>()
    private val executor = Executors.newSingleThreadExecutor()
    private val service = ImportOrdersService(importBatches, orders, parser, executor)

    @Test
    fun `aceita o upload, persiste o batch como PENDING com a contagem de linhas e devolve o id`() {
        val saved = slot<ImportBatch>()
        every { importBatches.save(capture(saved)) } answers { saved.captured }
        every { importBatches.findById(any()) } returns Optional.empty()
        every { parser.parse(any()) } returns emptyList()

        val threeDataRows = "header\nrow1\nrow2\nrow3".byteInputStream()

        val accepted = service.startImport("orders.csv", threeDataRows)

        assertThat(accepted.status).isEqualTo(ImportBatchStatus.PENDING)
        assertThat(saved.captured.fileName).isEqualTo("orders.csv")
        assertThat(saved.captured.progress.totalRows).isEqualTo(3)
    }

    @Test
    fun `processa em background, cria um Order por pedido agrupado e marca o batch como COMPLETED`() {
        val batch = ImportBatch(fileName = "orders.csv")
        every { importBatches.save(any()) } returns batch
        every { importBatches.findById(batch.id) } returns Optional.of(batch)
        every { orders.save(any()) } answers { firstArg() }
        every { parser.parse(any()) } returns listOf(
            ImportRow("ERP-SAP", "PO-001", UUID.randomUUID(), Instant.now(), "SKU-1", 2),
            ImportRow("ERP-SAP", "PO-002", UUID.randomUUID(), Instant.now(), "SKU-2", 1),
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
    fun `findStatus devolve null quando o batch nao existe`() {
        every { importBatches.findById(any()) } returns Optional.empty()

        assertThat(service.findStatus(UUID.randomUUID())).isNull()
    }
}
