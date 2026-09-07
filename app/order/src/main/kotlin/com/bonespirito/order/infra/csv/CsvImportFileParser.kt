package com.bonespirito.order.infra.csv

import com.bonespirito.order.application.port.ImportFileParser
import com.bonespirito.order.domain.ImportRow
import org.springframework.stereotype.Component
import java.io.InputStream
import java.time.Instant
import java.util.UUID

@Component
class CsvImportFileParser : ImportFileParser {

    override fun parse(content: InputStream): List<ImportRow> =
        content.bufferedReader().readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map(::toRow)

    private fun toRow(line: String): ImportRow {
        val columns = line.split(",")
        return ImportRow(
            sourceSystemCode = columns[COL_SOURCE_SYSTEM_CODE],
            sourceOrderNumber = columns[COL_SOURCE_ORDER_NUMBER],
            customerId = UUID.fromString(columns[COL_CUSTOMER_ID]),
            orderCreatedAt = Instant.parse(columns[COL_ORDER_CREATED_AT]),
            sku = columns[COL_SKU],
            quantity = columns[COL_QUANTITY].trim().toInt(),
        )
    }

    companion object {
        private const val COL_SOURCE_SYSTEM_CODE = 0
        private const val COL_SOURCE_ORDER_NUMBER = 1
        private const val COL_CUSTOMER_ID = 2
        private const val COL_ORDER_CREATED_AT = 3
        private const val COL_SKU = 4
        private const val COL_QUANTITY = 5
    }
}
