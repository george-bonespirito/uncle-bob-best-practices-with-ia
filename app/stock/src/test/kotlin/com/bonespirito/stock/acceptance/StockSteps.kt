package com.bonespirito.stock.acceptance

import com.bonespirito.stock.domain.StockState
import com.bonespirito.stock.domain.policy.ReservationRequest
import com.bonespirito.stock.domain.policy.StockValidationResult
import com.bonespirito.stock.domain.policy.availableQuantity
import com.bonespirito.stock.domain.policy.releaseReservation
import com.bonespirito.stock.domain.policy.reserveAll
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import java.util.UUID

class StockSteps {

    private var state = StockState(balances = emptyMap())
    private var result: StockValidationResult? = null
    private var reservationsBeforeRequest = 0
    private var lastReservationId: UUID? = null

    @Given("um SKU {string} com Stock Balance {int}")
    fun skuComStockBalance(sku: String, balance: Int) {
        state = state.copy(balances = state.balances + (sku to balance))
    }

    @Given("nenhum registro de estoque para {string}")
    fun nenhumRegistroDeEstoque(sku: String) {
        assertThat(state.balances).doesNotContainKey(sku)
    }

    @Given("uma Stock Reservation de {int} unidades de {string}")
    fun stockReservationExistente(quantidade: Int, sku: String) {
        val reservationId = UUID.randomUUID()
        val outcome = reserveAll(state, listOf(ReservationRequest(sku, quantidade)), reservationId)
        state = (outcome as StockValidationResult.Reserved).state
        lastReservationId = reservationId
    }

    @When("o Order pede {int} unidade(s) de {string}")
    fun orderPedeUmItem(quantidade: Int, sku: String) {
        pede(ReservationRequest(sku, quantidade))
    }

    @When("o Order pede {int} unidade(s) de {string} e {int} unidade(s) de {string}")
    fun orderPedeDoisItens(quantidadeA: Int, skuA: String, quantidadeB: Int, skuB: String) {
        pede(ReservationRequest(skuA, quantidadeA), ReservationRequest(skuB, quantidadeB))
    }

    @When("a compensação libera a Stock Reservation pelo stockReservationId")
    fun compensacaoLiberaPeloId() {
        state = releaseReservation(state, requireNotNull(lastReservationId))
    }

    @When("a compensação libera um stockReservationId diferente")
    fun compensacaoLiberaOutroId() {
        state = releaseReservation(state, UUID.randomUUID())
    }

    @Then("os itens são reservados")
    fun itensReservados() {
        assertThat(result).isInstanceOf(StockValidationResult.Reserved::class.java)
    }

    @Then("existe uma única Stock Reservation cobrindo {string} com {int} e {string} com {int}")
    fun umaUnicaReservation(skuA: String, quantidadeA: Int, skuB: String, quantidadeB: Int) {
        val reserved = result as StockValidationResult.Reserved
        assertThat(reserved.state.reservations).containsExactly(reserved.reservation)
        assertThat(reserved.reservation.lines).containsExactlyInAnyOrderEntriesOf(
            mapOf(skuA to quantidadeA, skuB to quantidadeB),
        )
    }

    @Then("a resposta é Out of Stock para {string}")
    fun respostaOutOfStock(sku: String) {
        val notReserved = result as StockValidationResult.NotReserved
        assertThat(notReserved.outOfStock.map { it.sku }).contains(sku)
    }

    @Then("a resposta não é Out of Stock")
    fun respostaNaoOutOfStock() {
        assertThat((result as StockValidationResult.NotReserved).outOfStock).isEmpty()
    }

    @Then("a resposta é Stock Record Not Found para {string}")
    fun respostaStockRecordNotFound(sku: String) {
        assertThat((result as StockValidationResult.NotReserved).stockRecordNotFound).contains(sku)
    }

    @Then("a resposta não é Stock Record Not Found")
    fun respostaNaoStockRecordNotFound() {
        assertThat((result as StockValidationResult.NotReserved).stockRecordNotFound).isEmpty()
    }

    @Then("nenhuma Stock Reservation é criada")
    fun nenhumaReservationCriada() {
        assertThat(result).isInstanceOf(StockValidationResult.NotReserved::class.java)
        assertThat(state.reservations).hasSize(reservationsBeforeRequest)
    }

    @Then("a Available Quantity de {string} é {int}")
    fun availableQuantityE(sku: String, esperada: Int) {
        assertThat(currentState().let { availableQuantity(it, sku) }).isEqualTo(esperada)
    }

    @Then("a Available Quantity de {string} continua {int}")
    fun availableQuantityContinua(sku: String, esperada: Int) {
        availableQuantityE(sku, esperada)
    }

    @Then("o Stock Balance de {string} continua {int}")
    fun stockBalanceContinua(sku: String, esperado: Int) {
        assertThat(currentState().balances[sku]).isEqualTo(esperado)
    }

    private fun pede(vararg itens: ReservationRequest) {
        reservationsBeforeRequest = state.reservations.size
        result = reserveAll(state, itens.toList(), UUID.randomUUID())
    }

    // Depois de uma reserva bem-sucedida o estado novo vem no resultado; antes disso,
    // ou depois de uma recusa, o estado vigente é o que o cenário já tinha.
    private fun currentState(): StockState =
        (result as? StockValidationResult.Reserved)?.state ?: state
}
