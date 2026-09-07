package com.bonespirito.order.domain.policy

import com.bonespirito.order.domain.ImportRow
import java.time.Instant
import java.util.UUID

data class GroupedOrder(
    val sourceSystemCode: String,
    val sourceOrderNumber: String,
    val customerId: UUID,
    val orderCreatedAt: Instant,
    val items: List<ImportRow>,
)

/**
 * Linhas com o mesmo par (source_system_code, source_order_number) formam um único Order —
 * a planilha é denormalizada, o cabeçalho do pedido se repete em cada linha de item.
 */
fun groupIntoOrders(rows: List<ImportRow>): List<GroupedOrder> =
    rows.groupBy { it.sourceSystemCode to it.sourceOrderNumber }
        .map { (key, groupRows) ->
            val first = groupRows.first()
            GroupedOrder(
                sourceSystemCode = key.first,
                sourceOrderNumber = key.second,
                customerId = first.customerId,
                orderCreatedAt = first.orderCreatedAt,
                items = groupRows,
            )
        }
