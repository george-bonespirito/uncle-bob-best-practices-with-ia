package com.bonespirito.order.infra.persistence

import com.bonespirito.order.application.port.OrderRepository
import com.bonespirito.order.domain.Order
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

/** Adapter JPA do [OrderRepository]: o Spring Data fica aqui dentro, fora de `application`. */
@Repository
class JpaOrderRepository internal constructor(
    private val springData: SpringDataOrderRepository,
) : OrderRepository {

    override fun save(order: Order): Order = springData.save(order)

    override fun findExistingSourceOrderNumbers(
        sourceSystemCode: String,
        sourceOrderNumbers: Collection<String>,
    ): List<String> = springData.findExistingSourceOrderNumbers(sourceSystemCode, sourceOrderNumbers)

    override fun findAll(): List<Order> = springData.findAll()
}

internal interface SpringDataOrderRepository : JpaRepository<Order, UUID> {

    /**
     * Usa o índice do UNIQUE (source_system_code, source_order_number) de V2, com o código
     * de origem como coluna líder.
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
