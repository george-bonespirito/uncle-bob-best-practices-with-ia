package com.bonespirito.stock.domain.policy

import com.bonespirito.stock.domain.StockReservation
import com.bonespirito.stock.domain.StockState
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class StockValidationTest {

    private val reservationId = UUID.randomUUID()

    private fun state(vararg balances: Pair<String, Int>, reservations: List<StockReservation> = emptyList()) =
        StockState(balances = mapOf(*balances), reservations = reservations)

    private fun outcome(state: StockState, vararg items: Pair<String, Int>) =
        reserveAll(state, items.map { (sku, quantity) -> ReservationRequest(sku, quantity) }, reservationId)

    @Test
    fun `reserva todos os itens com uma unica Stock Reservation`() {
        val result = outcome(state("SKU-1" to 10, "SKU-2" to 5), "SKU-1" to 3, "SKU-2" to 5)

        val reserved = result as StockValidationResult.Reserved
        assertThat(reserved.reservation.id).isEqualTo(reservationId)
        assertThat(reserved.reservation.lines).containsExactlyInAnyOrderEntriesOf(mapOf("SKU-1" to 3, "SKU-2" to 5))
        assertThat(reserved.state.reservations).containsExactly(reserved.reservation)
    }

    @Test
    fun `reserva preserva o Stock Balance e as reservas anteriores`() {
        val anterior = StockReservation(UUID.randomUUID(), mapOf("SKU-1" to 2))
        val inicial = state("SKU-1" to 10, reservations = listOf(anterior))

        val reserved = outcome(inicial, "SKU-1" to 3) as StockValidationResult.Reserved

        assertThat(reserved.state.balances).isEqualTo(inicial.balances)
        assertThat(reserved.state.reservations).containsExactly(anterior, reserved.reservation)
    }

    @Test
    fun `pedir exatamente a Available Quantity reserva`() {
        val result = outcome(state("SKU-1" to 10), "SKU-1" to 10)

        assertThat(result).isInstanceOf(StockValidationResult.Reserved::class.java)
    }

    @Test
    fun `pedir uma unidade a mais que a Available Quantity e Out of Stock`() {
        val result = outcome(state("SKU-1" to 10), "SKU-1" to 11)

        assertThat(result).isEqualTo(
            StockValidationResult.NotReserved(
                stockRecordNotFound = emptyList(),
                outOfStock = listOf(OutOfStockItem("SKU-1", requested = 11, available = 10)),
            ),
        )
    }

    @Test
    fun `quantidade parcial nao existe - pedir 10 com 4 disponiveis nao reserva nada`() {
        val reserva = StockReservation(UUID.randomUUID(), mapOf("SKU-1" to 6))

        val result = outcome(state("SKU-1" to 10, reservations = listOf(reserva)), "SKU-1" to 10)

        val notReserved = result as StockValidationResult.NotReserved
        assertThat(notReserved.outOfStock).containsExactly(OutOfStockItem("SKU-1", requested = 10, available = 4))
    }

    // equals de data class não chama os getters: estas asserções leem cada campo do
    // resultado real, que é o que o Order vai consumir.
    @Test
    fun `Out of Stock expoe sku, quantidade pedida e quantidade disponivel`() {
        val result = outcome(state("SKU-1" to 10), "SKU-1" to 11) as StockValidationResult.NotReserved

        val item = result.outOfStock.single()

        assertThat(item.sku).isEqualTo("SKU-1")
        assertThat(item.requested).isEqualTo(11)
        assertThat(item.available).isEqualTo(10)
    }

    @Test
    fun `Stock Record Not Found expoe os SKUs sem registro`() {
        val result = outcome(state("SKU-1" to 10), "SKU-8" to 1, "SKU-9" to 2) as StockValidationResult.NotReserved

        assertThat(result.stockRecordNotFound).containsExactly("SKU-8", "SKU-9")
    }

    @Test
    fun `falta em um item recusa o conjunto inteiro, listando so o que faltou`() {
        val result = outcome(state("SKU-1" to 10, "SKU-2" to 2, "SKU-3" to 9), "SKU-1" to 3, "SKU-2" to 5, "SKU-3" to 9)

        val notReserved = result as StockValidationResult.NotReserved
        assertThat(notReserved.outOfStock).containsExactly(OutOfStockItem("SKU-2", requested = 5, available = 2))
        assertThat(notReserved.stockRecordNotFound).isEmpty()
    }

    @Test
    fun `Stock Balance zero com registro e Out of Stock, nao Stock Record Not Found`() {
        val result = outcome(state("SKU-1" to 0), "SKU-1" to 1)

        val notReserved = result as StockValidationResult.NotReserved
        assertThat(notReserved.outOfStock).containsExactly(OutOfStockItem("SKU-1", requested = 1, available = 0))
        assertThat(notReserved.stockRecordNotFound).isEmpty()
    }

    @Test
    fun `SKU sem registro e Stock Record Not Found, nao Out of Stock`() {
        val result = outcome(state("SKU-1" to 10), "SKU-9" to 1)

        assertThat(result).isEqualTo(
            StockValidationResult.NotReserved(stockRecordNotFound = listOf("SKU-9"), outOfStock = emptyList()),
        )
    }

    @Test
    fun `registro ausente e falta no mesmo conjunto saem separados, sem precedencia decidida aqui`() {
        val result = outcome(state("SKU-1" to 1), "SKU-1" to 5, "SKU-9" to 1)

        assertThat(result).isEqualTo(
            StockValidationResult.NotReserved(
                stockRecordNotFound = listOf("SKU-9"),
                outOfStock = listOf(OutOfStockItem("SKU-1", requested = 5, available = 1)),
            ),
        )
    }

    @Test
    fun `mesmo SKU em duas linhas conta pela soma`() {
        val result = outcome(state("SKU-1" to 10), "SKU-1" to 6, "SKU-1" to 6)

        val notReserved = result as StockValidationResult.NotReserved
        assertThat(notReserved.outOfStock).containsExactly(OutOfStockItem("SKU-1", requested = 12, available = 10))
    }

    @Test
    fun `mesmo SKU em duas linhas que cabem vira uma linha so na reserva`() {
        val result = outcome(state("SKU-1" to 10), "SKU-1" to 6, "SKU-1" to 4)

        val reserved = result as StockValidationResult.Reserved
        assertThat(reserved.reservation.lines).containsExactlyEntriesOf(mapOf("SKU-1" to 10))
    }

    @Test
    fun `conjunto vazio nao e uma reserva`() {
        assertThatThrownBy { reserveAll(state("SKU-1" to 10), emptyList(), reservationId) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `item com quantidade zero ou negativa nao existe`() {
        assertThatThrownBy { ReservationRequest("SKU-1", 0) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ReservationRequest("SKU-1", -1) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(ReservationRequest("SKU-1", 1).quantity).isEqualTo(1)
    }
}
