package com.bonespirito.order.domain.policy

import com.bonespirito.order.domain.ImportRow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GroupedOrderTest {

    @Test
    fun `linhas sem pedidos nao geram grupo algum`() {
        assertThat(groupIntoOrders(emptyList())).isEmpty()
    }

    @Test
    fun `linhas com o mesmo par source_system_code e source_order_number formam um unico Order`() {
        val customerId = UUID.randomUUID()
        val createdAt = Instant.now()
        val rows = listOf(
            ImportRow("ERP-SAP", "PO-001", customerId, createdAt, "SKU-1", 2),
            ImportRow("ERP-SAP", "PO-001", customerId, createdAt, "SKU-2", 1),
            ImportRow("ERP-SAP", "PO-002", customerId, createdAt, "SKU-3", 5),
        )

        val grouped = groupIntoOrders(rows)

        assertThat(grouped).hasSize(2)
        val po001 = grouped.first { it.sourceOrderNumber == "PO-001" }
        assertThat(po001.items).hasSize(2)
        assertThat(po001.customerId).isEqualTo(customerId)
    }
}
