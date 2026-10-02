package com.bonespirito.stock.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class StockStateTest {

    @Test
    fun `Stock Balance zero e um saldo valido`() {
        val state = StockState(balances = mapOf("SKU-1" to 0))

        assertThat(state.balances).containsEntry("SKU-1", 0)
    }

    @Test
    fun `Stock Balance negativo nao existe`() {
        assertThatThrownBy { StockState(balances = mapOf("SKU-1" to -1)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `Stock Reservation precisa cobrir ao menos um SKU`() {
        assertThatThrownBy { StockReservation(UUID.randomUUID(), emptyMap()) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `Stock Reservation com quantidade zero ou negativa nao existe`() {
        assertThatThrownBy { StockReservation(UUID.randomUUID(), mapOf("SKU-1" to 0)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { StockReservation(UUID.randomUUID(), mapOf("SKU-1" to -2)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `Stock Reservation com quantidade positiva e valida`() {
        val id = UUID.randomUUID()

        val reservation = StockReservation(id, mapOf("SKU-1" to 1))

        assertThat(reservation.id).isEqualTo(id)
        assertThat(reservation.lines).containsEntry("SKU-1", 1)
    }
}
