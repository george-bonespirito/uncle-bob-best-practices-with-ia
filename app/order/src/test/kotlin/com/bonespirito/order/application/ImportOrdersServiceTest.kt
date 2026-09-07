package com.bonespirito.order.application

import com.bonespirito.order.application.port.ImportBatchRepository
import com.bonespirito.order.domain.ImportBatch
import com.bonespirito.order.domain.ImportBatchStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ImportOrdersServiceTest {

    private val importBatches = mockk<ImportBatchRepository>()
    private val executor = Executors.newSingleThreadExecutor()
    private val service = ImportOrdersService(importBatches, executor)

    @Test
    fun `aceita o upload, persiste o batch como PENDING com a contagem de linhas e devolve o id`() {
        val saved = slot<ImportBatch>()
        every { importBatches.save(capture(saved)) } answers { saved.captured }
        every { importBatches.findById(any()) } returns Optional.empty()

        val threeDataRows = "header\nrow1\nrow2\nrow3".byteInputStream()

        val accepted = service.startImport("orders.csv", threeDataRows)

        assertThat(accepted.status).isEqualTo(ImportBatchStatus.PENDING)
        assertThat(saved.captured.fileName).isEqualTo("orders.csv")
        assertThat(saved.captured.progress.totalRows).isEqualTo(3)
    }

    @Test
    fun `submete o processamento em background e marca o batch como COMPLETED`() {
        val batch = ImportBatch(fileName = "orders.csv")
        every { importBatches.save(any()) } returns batch
        every { importBatches.findById(batch.id) } returns Optional.of(batch)

        service.startImport("orders.csv", "header\nrow1".byteInputStream())
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.SECONDS)

        assertThat(batch.status).isEqualTo(ImportBatchStatus.COMPLETED)
        assertThat(batch.completedAt).isNotNull()
        verify { importBatches.save(batch) }
    }
}
