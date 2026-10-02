package com.bonespirito.stock.domain.policy

import com.bonespirito.stock.domain.StockReservation
import com.bonespirito.stock.domain.StockState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ReservationReleaseTest {

    private val first = StockReservation(UUID.randomUUID(), mapOf("SKU-1" to 4))
    private val second = StockReservation(UUID.randomUUID(), mapOf("SKU-1" to 3))
    private val state = StockState(balances = mapOf("SKU-1" to 10), reservations = listOf(first, second))

    @Test
    fun `libera so a reserva do id informado e devolve as unidades`() {
        val released = releaseReservation(state, first.id)

        assertThat(released.reservations).containsExactly(second)
        assertThat(availableQuantity(released, "SKU-1")).isEqualTo(7)
    }

    @Test
    fun `liberacao nao mexe no Stock Balance`() {
        assertThat(releaseReservation(state, first.id).balances).isEqualTo(state.balances)
    }

    @Test
    fun `liberar um id ja liberado e no-op, nao erro`() {
        val once = releaseReservation(state, first.id)

        assertThat(releaseReservation(once, first.id)).isEqualTo(once)
    }

    @Test
    fun `liberar um id que nao existe nao libera nenhuma reserva`() {
        assertThat(releaseReservation(state, UUID.randomUUID())).isEqualTo(state)
    }
}
