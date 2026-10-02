package com.bonespirito.customers.acceptance

import com.bonespirito.customers.domain.Customer
import com.bonespirito.customers.domain.DeliveryAddress
import com.bonespirito.customers.domain.Document
import com.bonespirito.customers.domain.policy.CustomerEnrichment
import com.bonespirito.customers.domain.policy.enrichCustomer
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat

class CustomerEnrichmentSteps {

    private var customer: Customer? = null
    private var result: CustomerEnrichment? = null

    @Given("um Customer cadastrado {string} com documento {string}")
    fun umCustomerCadastrado(name: String, digits: String) {
        customer = Customer.create(name, Document.of(digits), DeliveryAddress())
    }

    @Given("um customer_id que não existe no cadastro")
    fun customerIdInexistente() {
        customer = null
    }

    @When("o Order pede o enriquecimento do customer_id")
    fun oOrderPedeEnriquecimento() {
        result = enrichCustomer(customer)
    }

    @When("o Order pede o enriquecimento")
    fun oOrderPedeEnriquecimentoSemId() {
        result = enrichCustomer(customer)
    }

    @Then("o Snapshot traz o Name {string}")
    fun snapshotTrazName(name: String) {
        assertThat(snapshot().name).isEqualTo(name)
    }

    @Then("o Snapshot traz o Document {string}")
    fun snapshotTrazDocument(digits: String) {
        assertThat(snapshot().document).isEqualTo(Document.of(digits))
    }

    @Then("o Snapshot traz o Default Delivery Address")
    fun snapshotTrazDefaultDeliveryAddress() {
        assertThat(snapshot().defaultDeliveryAddress).isEqualTo(customer!!.defaultDeliveryAddress)
    }

    @Then("a resposta é Customer Not Found")
    fun respostaCustomerNotFound() {
        assertThat(result).isEqualTo(CustomerEnrichment.CustomerNotFound)
    }

    private fun snapshot() =
        (result as CustomerEnrichment.Enriched).snapshot
}
