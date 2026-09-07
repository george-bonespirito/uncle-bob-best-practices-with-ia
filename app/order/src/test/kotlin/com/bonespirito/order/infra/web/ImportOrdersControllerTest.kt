package com.bonespirito.order.infra.web

import com.bonespirito.order.application.ImportBatchAccepted
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
    private val controller = ImportOrdersController(importOrders)

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
}
