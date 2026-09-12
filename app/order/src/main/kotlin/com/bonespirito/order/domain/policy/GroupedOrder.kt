package com.bonespirito.order.domain.policy

import com.bonespirito.order.domain.NumberedRow
import java.time.Instant
import java.util.UUID

data class GroupedOrder(
    val sourceSystemCode: String,
    val sourceOrderNumber: String,
    val customerId: UUID,
    val orderCreatedAt: Instant,
    val items: List<NumberedRow>,
)

/**
 * Linhas com o mesmo par (source_system_code, source_order_number) formam um único Order —
 * a planilha é denormalizada, o cabeçalho do pedido se repete em cada linha de item.
 */
fun mergeQuantitiesBySku(rows: List<NumberedRow>): List<Pair<String, Int>> =
    rows.groupBy { it.row.sku }
        .map { (sku, group) -> sku to group.sumOf { it.row.quantity } }

fun groupIntoOrders(rows: List<NumberedRow>): List<GroupedOrder> =
    rows.groupBy { it.row.sourceSystemCode to it.row.sourceOrderNumber }
        .map { (key, groupRows) ->
            val first = groupRows.first().row
            GroupedOrder(
                sourceSystemCode = key.first,
                sourceOrderNumber = key.second,
                customerId = first.customerId,
                orderCreatedAt = first.orderCreatedAt,
                items = groupRows,
            )
        }

data class DuplicatePartition(val new: List<GroupedOrder>, val duplicates: List<GroupedOrder>)

/**
 * Duplicata é contra o conjunto já importado em lotes anteriores — dentro do mesmo lote,
 * linhas com o mesmo par já foram mescladas num único GroupedOrder por groupIntoOrders.
 */
fun partitionByDuplicate(grouped: List<GroupedOrder>, existingKeys: Set<Pair<String, String>>): DuplicatePartition {
    val (duplicates, new) = grouped.partition { existingKeys.contains(it.sourceSystemCode to it.sourceOrderNumber) }
    return DuplicatePartition(new, duplicates)
}
