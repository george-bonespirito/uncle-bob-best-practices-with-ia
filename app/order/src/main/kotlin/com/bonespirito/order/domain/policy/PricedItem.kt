package com.bonespirito.order.domain.policy

import com.bonespirito.order.domain.FulfillmentStatus
import java.math.BigDecimal

data class PricedItem(
    val unitPrice: BigDecimal,
    val quantity: Int,
    val fulfillment: FulfillmentStatus,
)

/**
 * Order Total é derivado, nunca armazenado (ADR order/0013): soma de unit_price × quantity
 * dos itens FULFILLED. Um item pode virar NOT_FULFILLED depois da precificação — inclusive
 * na Invoice Generation — e um total gravado ficaria errado em silêncio.
 */
fun orderTotal(items: List<PricedItem>): BigDecimal =
    items
        .filter { it.fulfillment == FulfillmentStatus.FULFILLED }
        .fold(BigDecimal.ZERO) { total, item -> total + item.unitPrice * BigDecimal(item.quantity) }
