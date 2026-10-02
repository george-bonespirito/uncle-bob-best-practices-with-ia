package com.bonespirito.products.acceptance

import com.bonespirito.products.domain.PhysicalAttributes
import com.bonespirito.products.domain.Product
import com.bonespirito.products.domain.ProductStatus
import com.bonespirito.products.domain.Sku
import com.bonespirito.products.domain.policy.ProductEnrichment
import com.bonespirito.products.domain.policy.ProductSnapshot
import com.bonespirito.products.domain.policy.enrichProduct
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.cucumber.spring.ScenarioScope
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import java.math.BigDecimal

/**
 * Steps da fatia de domínio do Products (architect-000069): só policy pura —
 * catálogo em memória, sem REST nem persistência.
 */
@ScenarioScope
class ProductEnrichmentSteps {

    private val catalog = mutableMapOf<Sku, Product>()
    private var lastResult: ProductEnrichment? = null

    private fun attrs() = PhysicalAttributes(
        weight = BigDecimal("2.50"),
        length = BigDecimal("30"),
        width = BigDecimal("20"),
        height = BigDecimal("15"),
    )

    @Given("o Product {string} {string} categoria {string} com atributos físicos cadastrados")
    fun productComAtributos(sku: String, name: String, category: String) {
        catalog[Sku(sku)] = Product.create(Sku(sku), name, "sem descrição", category, attrs())
    }

    @Given("o Product {string} {string} com Description {string} e atributos físicos cadastrados")
    fun productComDescription(sku: String, name: String, description: String) {
        catalog[Sku(sku)] = Product.create(Sku(sku), name, description, "Ferramentas", attrs())
    }

    @Given("o Product {string} {string} categoria {string} com atributos físicos cadastrados e status {string}")
    fun productComStatus(sku: String, name: String, category: String, status: String) {
        val created = Product.create(Sku(sku), name, "sem descrição", category, attrs())
        catalog[Sku(sku)] = if (ProductStatus.valueOf(status) == ProductStatus.INACTIVE) {
            created.deactivate()
        } else {
            created
        }
    }

    @Given("o sku {string} não existe no cadastro")
    fun skuInexistente(sku: String) {
        catalog.remove(Sku(sku))
    }

    @When("o Order pede o enriquecimento do sku {string}")
    fun pedeEnriquecimento(sku: String) {
        lastResult = enrichProduct(Sku(sku)) { catalog[it] }
    }

    @When("alguém tenta cadastrar um Product sem Physical Attributes")
    fun tentaCadastrarSemAtributos() {
        assertThatThrownBy {
            Product.create(Sku("SKU-3"), "Lixadeira", "sem descrição", "Ferramentas", null)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Physical Attributes são obrigatórios")
    }

    @Then("o Snapshot traz o Name {string}")
    fun snapshotTrazName(name: String) {
        assertThat((lastResult as ProductEnrichment.Enriched).snapshot.name).isEqualTo(name)
    }

    @And("o Snapshot traz a Category {string}")
    fun snapshotTrazCategory(category: String) {
        assertThat((lastResult as ProductEnrichment.Enriched).snapshot.category).isEqualTo(category)
    }

    @And("o Snapshot traz o Product Status {string}")
    fun snapshotTrazStatus(status: String) {
        assertThat((lastResult as ProductEnrichment.Enriched).snapshot.status)
            .isEqualTo(ProductStatus.valueOf(status))
    }

    @Then("o Snapshot não contém a Description")
    fun snapshotSemDescription() {
        val snapshot = (lastResult as ProductEnrichment.Enriched).snapshot
        assertThat(snapshot).isEqualTo(
            ProductSnapshot("Furadeira de Impacto", "Ferramentas", ProductStatus.ACTIVE),
        )
    }

    @Then("o Snapshot não contém peso nem dimensões")
    fun snapshotSemAtributosFisicos() {
        val snapshot = (lastResult as ProductEnrichment.Enriched).snapshot
        assertThat(snapshot).isEqualTo(
            ProductSnapshot("Furadeira de Impacto", "Ferramentas", ProductStatus.ACTIVE),
        )
    }

    @Then("a resposta não é Product Not Found")
    fun naoProductNotFound() {
        assertThat(lastResult).isNotEqualTo(ProductEnrichment.ProductNotFound)
    }

    @Then("a resposta é Product Not Found")
    fun productNotFound() {
        assertThat(lastResult).isEqualTo(ProductEnrichment.ProductNotFound)
    }

    @Then("a criação é recusada")
    fun criacaoRecusada() = Unit

    @And("a razão é que Product sem eles é inútil pro Route")
    fun razaoAtributosObrigatorios() = Unit

    @And("a razão é que a task espera o cadastro, não recusa o pedido")
    fun razaoTaskEspera() = Unit
}
