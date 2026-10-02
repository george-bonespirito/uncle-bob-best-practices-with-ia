package com.bonespirito.pricing.acceptance

import com.bonespirito.pricing.domain.Currency
import com.bonespirito.pricing.domain.Sku
import com.bonespirito.pricing.domain.UnitPrice
import com.bonespirito.pricing.domain.policy.PricingEnrichment
import com.bonespirito.pricing.domain.policy.enrichPrice
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.cucumber.spring.ScenarioScope
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import java.math.BigDecimal

/**
 * Steps da fatia de domínio do Pricing (architect-000070): só policy pura —
 * catálogo em memória, sem REST nem persistência.
 */
@ScenarioScope
class PricingEnrichmentSteps {

    private val catalog = mutableMapOf<Sku, UnitPrice>()
    private var lastResult: PricingEnrichment? = null

    @Given("o catálogo tem o SKU {string} com Unit Price {string} na moeda {string}")
    fun catalogoComPreco(sku: String, amount: String, currency: String) {
        catalog[Sku(sku)] = UnitPrice(BigDecimal(amount), Currency(currency))
    }

    @Given("o SKU {string} não está no catálogo")
    fun skuAusente(sku: String) {
        catalog.remove(Sku(sku))
    }

    @When("o Order pede o preço do SKU {string}")
    fun pedePreco(sku: String) {
        lastResult = enrichPrice(Sku(sku)) { catalog[it] }
    }

    @When("alguém tenta cadastrar o Unit Price {string}")
    fun tentaCadastrarNegativo(amount: String) {
        assertThatThrownBy { UnitPrice(BigDecimal(amount), Currency("BRL")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Then("o Unit Price é {string}")
    fun unitPriceEsperado(amount: String) {
        val result = lastResult as PricingEnrichment.Enriched
        assertThat(result.unitPrice.amount).isEqualByComparingTo(BigDecimal(amount))
    }

    @And("a Currency é {string}")
    fun currencyEsperada(code: String) {
        val result = lastResult as PricingEnrichment.Enriched
        assertThat(result.unitPrice.currency).isEqualTo(Currency(code))
    }

    @Then("a resposta é Price Not Found")
    fun priceNotFound() {
        assertThat(lastResult).isEqualTo(PricingEnrichment.PriceNotFound)
    }

    @And("a resposta não é Price Not Found")
    fun naoPriceNotFound() {
        assertThat(lastResult).isNotEqualTo(PricingEnrichment.PriceNotFound)
    }

    @And("a razão é que ausência de preço não recusa o pedido — a task espera o cadastro")
    fun razaoAusencia() = Unit

    @Then("a criação é recusada")
    fun criacaoRecusada() {
        // recusa já verificada no step de When (exceção capturada lá)
    }

    @And("a razão é que preço inválido é recusa, não ausência")
    fun razaoPrecoInvalido() = Unit

    @And("a razão é que a assinatura do enriquecimento não recebe cliente")
    fun razaoSemCliente() = Unit
}
