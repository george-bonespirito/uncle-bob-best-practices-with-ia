package com.bonespirito.order.infra.csv

import com.bonespirito.order.application.port.ImportFileParser
import com.bonespirito.order.domain.policy.ParsedRow
import com.bonespirito.order.domain.policy.validateRow
import org.springframework.stereotype.Component
import java.io.InputStream

@Component
class CsvImportFileParser : ImportFileParser {

    override fun parse(content: InputStream): List<ParsedRow> =
        content.bufferedReader().readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .mapIndexed { index, line -> validateRow(index + 1, line.split(",")) }
}
