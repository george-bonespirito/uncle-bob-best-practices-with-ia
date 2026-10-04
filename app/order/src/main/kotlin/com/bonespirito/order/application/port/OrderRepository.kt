package com.bonespirito.order.application.port

import com.bonespirito.order.domain.Order
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID> {

    /**
     * Dos `sourceOrderNumbers` informados, os que já existem para o `sourceSystemCode` — a
     * consulta cobre só as chaves do lote e usa o índice do UNIQUE (source_system_code,
     * source_order_number) de V2, com o código de origem como coluna líder.
     */
    @Query(
        """
        select o.sourceOrderReference.sourceOrderNumber from Order o
        where o.sourceOrderReference.sourceSystemCode = :sourceSystemCode
          and o.sourceOrderReference.sourceOrderNumber in :sourceOrderNumbers
        """,
    )
    fun findExistingSourceOrderNumbers(
        @Param("sourceSystemCode") sourceSystemCode: String,
        @Param("sourceOrderNumbers") sourceOrderNumbers: Collection<String>,
    ): List<String>
}
