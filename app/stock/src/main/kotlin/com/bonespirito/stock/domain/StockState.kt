package com.bonespirito.stock.domain

import java.util.UUID

/**
 * Stock Reservation (CONTEXT.md): compromisso de unidades de um ou mais SKUs, com
 * identidade própria — o `stockReservationId` que a compensação recebe para liberar.
 * Não há expiração: nenhum campo de tempo existe aqui de propósito.
 */
data class StockReservation(val id: UUID, val lines: Map<String, Int>) {
    init {
        require(lines.isNotEmpty()) { "Stock Reservation precisa cobrir ao menos um SKU" }
        require(lines.values.all { it > 0 }) { "quantidade reservada precisa ser positiva: $lines" }
    }
}

/**
 * Stock Balance por SKU e as Stock Reservations vigentes sobre ele. O agrupamento não é
 * termo do glossário; existe porque a Available Quantity é derivada dos dois e não pode
 * ser calculada com só um deles. Imutável: cada operação devolve o estado novo.
 */
data class StockState(
    val balances: Map<String, Int>,
    val reservations: List<StockReservation> = emptyList(),
) {
    init {
        require(balances.values.all { it >= 0 }) { "Stock Balance não pode ser negativo: $balances" }
    }
}
