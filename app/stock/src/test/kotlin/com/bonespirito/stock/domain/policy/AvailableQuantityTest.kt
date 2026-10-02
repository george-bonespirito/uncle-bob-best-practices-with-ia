package com.bonespirito.stock.domain.policy

import com.bonespirito.stock.domain.StockReservation
import com.bonespirito.stock.domain.StockState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class AvailableQuantityTest {

    private fun reservation(vararg lines: Pair<String, Int>) = StockReservation(UUID.randomUUID(), mapOf(*lines))

    @Test
    fun `SKU sem registro nao tem Available Quantity`() {
        assertThat(availableQuantity(StockState(balances = emptyMap()), "SKU-1")).isNull()
    }

    @Test
    fun `sem reservas a Available Quantity e o Stock Balance`() {
        val state = StockState(balances = mapOf("SKU-1" to 10))

        assertThat(availableQuantity(state, "SKU-1")).isEqualTo(10)
    }

    @Test
    fun `Stock Balance zero com registro tem Available Quantity zero, nao nula`() {
        val state = StockState(balances = mapOf("SKU-1" to 0))

        assertThat(availableQuantity(state, "SKU-1")).isEqualTo(0)
    }

    @Test
    fun `reservas do mesmo SKU somam e saem do Available Quantity`() {
        val state = StockState(
            balances = mapOf("SKU-1" to 10),
            reservations = listOf(reservation("SKU-1" to 4), reservation("SKU-1" to 3)),
        )

        assertThat(availableQuantity(state, "SKU-1")).isEqualTo(3)
    }

    @Test
    fun `reserva de outro SKU nao afeta este`() {
        val state = StockState(
            balances = mapOf("SKU-1" to 10, "SKU-2" to 10),
            reservations = listOf(reservation("SKU-2" to 8)),
        )

        assertThat(availableQuantity(state, "SKU-1")).isEqualTo(10)
        assertThat(availableQuantity(state, "SKU-2")).isEqualTo(2)
    }

    @Test
    fun `uma reserva com varios SKUs desconta so a linha do SKU consultado`() {
        val state = StockState(
            balances = mapOf("SKU-1" to 10, "SKU-2" to 10),
            reservations = listOf(reservation("SKU-1" to 2, "SKU-2" to 5)),
        )

        assertThat(availableQuantity(state, "SKU-1")).isEqualTo(8)
        assertThat(availableQuantity(state, "SKU-2")).isEqualTo(5)
    }
}
