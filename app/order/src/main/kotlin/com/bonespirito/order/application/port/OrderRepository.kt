package com.bonespirito.order.application.port

import com.bonespirito.order.domain.Order

/** Port de persistência do Order (ADR 0009). O adapter mora em `infra.persistence`. */
interface OrderRepository {
    fun save(order: Order): Order

    /**
     * Dos `sourceOrderNumbers` informados, os que já existem para o `sourceSystemCode` — a
     * consulta cobre só as chaves do lote, não a tabela inteira.
     */
    fun findExistingSourceOrderNumbers(sourceSystemCode: String, sourceOrderNumbers: Collection<String>): List<String>
}
