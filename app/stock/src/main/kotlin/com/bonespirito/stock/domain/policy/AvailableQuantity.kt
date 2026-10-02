package com.bonespirito.stock.domain.policy

import com.bonespirito.stock.domain.StockState

/**
 * Available Quantity é derivada, nunca mantida à mão: Stock Balance menos o que está
 * reservado. `null` é Stock Record Not Found — SKU sem registro; saldo zero com registro
 * devolve 0, que é outra coisa (Out of Stock).
 */
fun availableQuantity(state: StockState, sku: String): Int? {
    val balance = state.balances[sku] ?: return null
    val reserved = state.reservations.sumOf { it.lines[sku] ?: 0 }
    return balance - reserved
}
