package com.bonespirito.stock.domain.policy

import com.bonespirito.stock.domain.StockState
import java.util.UUID

/**
 * Reservation Release: só por ação compensatória explícita, pelo `stockReservationId`.
 * Id já liberado — ou que nunca existiu, indistinguível sem guardar histórico — é no-op,
 * não erro. Não há expiração: nada aqui lê relógio.
 */
fun releaseReservation(state: StockState, reservationId: UUID): StockState =
    state.copy(reservations = state.reservations.filterNot { it.id == reservationId })
