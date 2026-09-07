package com.bonespirito.order.domain.policy

import com.bonespirito.order.domain.ImportRow
import com.bonespirito.order.domain.NumberedRow
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
            NumberedRow(1, ImportRow("ERP-SAP", "PO-001", customerId, createdAt, "SKU-1", 2)),
            NumberedRow(2, ImportRow("ERP-SAP", "PO-001", customerId, createdAt, "SKU-2", 1)),
            NumberedRow(3, ImportRow("ERP-SAP", "PO-002", customerId, createdAt, "SKU-3", 5)),
        )

        val grouped = groupIntoOrders(rows)

        assertThat(grouped).hasSize(2)
        val po001 = grouped.first { it.sourceOrderNumber == "PO-001" }
        assertThat(po001.items).hasSize(2)
        assertThat(po001.customerId).isEqualTo(customerId)
    }

    @Test
    fun `sem chaves ja existentes nenhum grupo e duplicata`() {
        val grouped = listOf(umGrupo("PO-001"))

        val partition = partitionByDuplicate(grouped, existingKeys = emptySet())

        assertThat(partition.new).isEqualTo(grouped)
        assertThat(partition.duplicates).isEmpty()
    }

    @Test
    fun `grupo com chave ja existente vira duplicata, os demais seguem novos`() {
        val novo = umGrupo("PO-001")
        val duplicado = umGrupo("PO-002")

        val partition = partitionByDuplicate(
            listOf(novo, duplicado),
            existingKeys = setOf("ERP-SAP" to "PO-002"),
        )

        assertThat(partition.new).containsExactly(novo)
        assertThat(partition.duplicates).containsExactly(duplicado)
    }

    private fun umGrupo(sourceOrderNumber: String): GroupedOrder {
        val row = ImportRow("ERP-SAP", sourceOrderNumber, UUID.randomUUID(), Instant.now(), "SKU-1", 1)
        return GroupedOrder(
            sourceSystemCode = "ERP-SAP",
            sourceOrderNumber = sourceOrderNumber,
            customerId = UUID.randomUUID(),
            orderCreatedAt = Instant.now(),
            items = listOf(NumberedRow(1, row)),
        )
    }
}
