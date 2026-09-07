package com.bonespirito.order.infra.web

import com.bonespirito.order.application.ImportBatchAccepted
import com.bonespirito.order.application.ImportBatchStatusQuery
import com.bonespirito.order.application.ImportBatchStatusView
import com.bonespirito.order.application.ImportOrders
import com.bonespirito.order.domain.ImportBatchStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import java.util.UUID

class ImportOrdersControllerTest {

    private val importOrders = mockk<ImportOrders>()
    private val importBatchStatus = mockk<ImportBatchStatusQuery>()
    private val controller = ImportOrdersController(importOrders, importBatchStatus)

    @Test
    fun `usa o nome original do arquivo quando presente`() {
        val file = MockMultipartFile("file", "orders.csv", "text/csv", "data".toByteArray())
        val batchId = UUID.randomUUID()
        every { importOrders.startImport("orders.csv", any()) } returns
            ImportBatchAccepted(batchId, ImportBatchStatus.PENDING)

        val response = controller.upload(file)

        assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED)
        assertThat(response.body?.importBatchId).isEqualTo(batchId)
        assertThat(response.body?.status).isEqualTo("PENDING")
    }

    @Test
    fun `usa o nome do campo do multipart quando o arquivo nao tem nome original`() {
        val file = MockMultipartFile("file", null, "text/csv", "data".toByteArray())
        every { importOrders.startImport("file", any()) } returns
            ImportBatchAccepted(UUID.randomUUID(), ImportBatchStatus.PENDING)

        controller.upload(file)

        verify { importOrders.startImport("file", any()) }
    }

    @Test
    fun `devolve o status do batch quando ele existe`() {
        val batchId = UUID.randomUUID()
        every { importBatchStatus.findStatus(batchId) } returns
            ImportBatchStatusView(
                batchId,
                ImportBatchStatus.COMPLETED,
                totalRows = 3,
                processedRows = 3,
                ordersCreated = 3,
            )

        val response = controller.status(batchId)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.status).isEqualTo("COMPLETED")
        assertThat(response.body?.ordersCreated).isEqualTo(3)
    }

    @Test
    fun `devolve 404 quando o batch nao existe`() {
        val batchId = UUID.randomUUID()
        every { importBatchStatus.findStatus(batchId) } returns null

        val response = controller.status(batchId)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }
}
