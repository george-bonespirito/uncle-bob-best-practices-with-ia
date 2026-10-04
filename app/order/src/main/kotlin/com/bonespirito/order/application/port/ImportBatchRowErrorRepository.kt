package com.bonespirito.order.application.port

import com.bonespirito.order.domain.ImportBatchRowError

/** Port de persistência dos erros de linha do lote (ADR 0009). O adapter mora em `infra.persistence`. */
interface ImportBatchRowErrorRepository {
    fun save(rowError: ImportBatchRowError): ImportBatchRowError
}
