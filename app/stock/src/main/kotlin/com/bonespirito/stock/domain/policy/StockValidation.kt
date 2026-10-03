package com.bonespirito.stock.domain.policy

import com.bonespirito.stock.domain.StockReservation
import com.bonespirito.stock.domain.StockState
import java.util.UUID

data class ReservationRequest(val sku: String, val quantity: Int) {
    init {
        require(quantity > 0) { "quantidade pedida precisa ser positiva: $sku=$quantity" }
    }
}

data class OutOfStockItem(val sku: String, val requested: Int, val available: Int)

/**
 * Desfecho da Stock Validation. Out of Stock e Stock Record Not Found saem em listas
 * separadas porque têm desfechos opostos no Order (cancela x trava em
 * AWAITING_MASTER_DATA) e o glossário não diz qual vence quando os dois ocorrem no
 * mesmo conjunto — quem decide é o Order, não este contexto.
 */
sealed interface StockValidationResult {
    data class Reserved(val state: StockState, val reservation: StockReservation) : StockValidationResult
    data class NotReserved(
        val stockRecordNotFound: List<String>,
        val outOfStock: List<OutOfStockItem>,
    ) : StockValidationResult
}

/**
 * Stock Validation: dado o conjunto de itens atendidos de um Order, reserva tudo ou não
 * reserva nada (ADR order/0014). Quantidade parcial não existe — item que pede mais do
 * que a Available Quantity é Out of Stock, nunca reservado em parte. Itens do mesmo SKU
 * contam pela soma, senão duas linhas que cabem sozinhas estourariam juntas.
 */
fun reserveAll(state: StockState, items: List<ReservationRequest>, reservationId: UUID): StockValidationResult {
    require(items.isNotEmpty()) { "Order sem itens atendidos não chega à Stock Validation" }
    val requested = items.groupBy { it.sku }.mapValues { (_, lines) -> lines.sumOf { it.quantity } }

    val stockRecordNotFound = requested.keys.filter { it !in state.balances }
    val outOfStock = requested.mapNotNull { (sku, quantity) ->
        availableQuantity(state, sku)?.takeIf { it < quantity }?.let { OutOfStockItem(sku, quantity, it) }
    }
    if (stockRecordNotFound.isNotEmpty() || outOfStock.isNotEmpty()) {
        return StockValidationResult.NotReserved(stockRecordNotFound, outOfStock)
    }

    val reservation = StockReservation(reservationId, requested)
    return StockValidationResult.Reserved(state.copy(reservations = state.reservations + reservation), reservation)
}
