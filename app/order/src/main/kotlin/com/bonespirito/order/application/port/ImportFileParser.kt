package com.bonespirito.order.application.port

import com.bonespirito.order.domain.policy.ParsedRow
import java.io.InputStream

interface ImportFileParser {
    fun parse(content: InputStream): List<ParsedRow>
}
