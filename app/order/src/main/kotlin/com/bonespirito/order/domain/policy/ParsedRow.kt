package com.bonespirito.order.domain.policy

import com.bonespirito.order.domain.ImportRow
import java.time.Instant
import java.util.UUID

sealed interface ParsedRow {
    data class Valid(val row: ImportRow) : ParsedRow
    data class Invalid(val rowNumber: Int, val reason: String) : ParsedRow
}

private const val COL_SOURCE_SYSTEM_CODE = 0
private const val COL_SOURCE_ORDER_NUMBER = 1
private const val COL_CUSTOMER_ID = 2
private const val COL_ORDER_CREATED_AT = 3
private const val COL_SKU = 4
private const val COL_QUANTITY = 5

/**
 * Só o que dá pra verificar dentro do próprio arquivo produz erro de linha (dado
 * obrigatório ausente) — existência de cliente/produto é responsabilidade da
 * Data Enrichment, não do import (ver Rejected Order Row, CONTEXT.md).
 */
fun validateRow(rowNumber: Int, columns: List<String>): ParsedRow {
    val sourceSystemCode = columns.getOrElse(COL_SOURCE_SYSTEM_CODE) { "" }
    val sourceOrderNumber = columns.getOrElse(COL_SOURCE_ORDER_NUMBER) { "" }
    val customerId = columns.getOrElse(COL_CUSTOMER_ID) { "" }
    val orderCreatedAt = columns.getOrElse(COL_ORDER_CREATED_AT) { "" }
    val sku = columns.getOrElse(COL_SKU) { "" }
    val quantity = columns.getOrElse(COL_QUANTITY) { "" }

    val missingFieldReason = listOf(
        "source_system_code" to sourceSystemCode,
        "source_order_number" to sourceOrderNumber,
        "customer_id" to customerId,
        "sku" to sku,
    ).firstOrNull { (_, value) -> value.isBlank() }?.let { (field, _) -> "$field ausente" }

    return missingFieldReason?.let { ParsedRow.Invalid(rowNumber, it) }
        ?: runCatching {
            ParsedRow.Valid(
                ImportRow(
                    sourceSystemCode = sourceSystemCode,
                    sourceOrderNumber = sourceOrderNumber,
                    customerId = UUID.fromString(customerId),
                    orderCreatedAt = Instant.parse(orderCreatedAt),
                    sku = sku,
                    quantity = quantity.trim().toInt(),
                ),
            )
        }.getOrElse { ParsedRow.Invalid(rowNumber, "linha em formato inválido: ${it.message}") }
}
