package com.bonespirito.customers.acceptance

import com.bonespirito.customers.domain.Customer
import com.bonespirito.customers.domain.DeliveryAddress
import com.bonespirito.customers.domain.CustomerType
import com.bonespirito.customers.domain.Document
import com.bonespirito.customers.domain.InvalidDocumentException
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat

class CustomerRegistrationSteps {

    private lateinit var documentDigits: String
    private var documentResult: Result<Document>? = null
    private lateinit var customerName: String
    private lateinit var customerDocument: Document
    private var customer: Customer? = null
    private var firstAddress: DeliveryAddress? = null

    @Given("o documento {string}")
    fun oDocumento(digits: String) {
        documentDigits = digits
    }

    @When("o documento é registrado")
    fun oDocumentoERegistrado() {
        documentResult = runCatching { Document.of(documentDigits) }
    }

    @Then("ele vira um Document válido")
    fun viraDocumentValido() {
        assertThat(documentResult!!.isSuccess).isTrue()
    }

    @Then("o Customer Type derivado é {string}")
    fun oCustomerTypeDerivado(expected: String) {
        assertThat(documentResult!!.getOrThrow().type).isEqualTo(CustomerType.valueOf(expected))
    }

    @Then("o Document é recusado com motivo de dígito verificador")
    fun documentoRecusadoDv() {
        val failure = documentResult!!.exceptionOrNull()
        assertThat(failure).isInstanceOf(InvalidDocumentException::class.java)
        assertThat((failure as InvalidDocumentException).reason).contains("dígito verificador")
    }

    @Then("o Document é recusado com motivo de tamanho")
    fun documentoRecusadoTamanho() {
        val failure = documentResult!!.exceptionOrNull()
        assertThat(failure).isInstanceOf(InvalidDocumentException::class.java)
        assertThat((failure as InvalidDocumentException).reason).contains("tamanho")
    }

    @Given("um Customer {string} com documento {string}")
    fun umCustomerComDocumento(name: String, digits: String) {
        customerName = name
        customerDocument = Document.of(digits)
    }

    @When("o primeiro Delivery Address é registrado")
    fun primeiroDeliveryAddressRegistrado() {
        firstAddress = DeliveryAddress()
        customer = Customer.create(customerName, customerDocument, firstAddress)
    }

    @Given("o primeiro Delivery Address foi registrado")
    fun primeiroDeliveryAddressFoiRegistrado() = primeiroDeliveryAddressRegistrado()

    @Then("o Customer é válido")
    fun oCustomerEValido() {
        assertThat(customer).isNotNull
    }

    @Then("esse endereço é o Default Delivery Address")
    fun esseEnderecoEDefault() {
        assertThat(customer!!.defaultDeliveryAddress).isEqualTo(firstAddress)
    }

    @When("tento criar o Customer sem nenhum Delivery Address")
    fun tentoCriarSemEndereco() {
        customer = runCatching {
            Customer.create(customerName, customerDocument, firstAddress = null)
        }.getOrNull()
    }

    @Then("a criação é recusada por ausência de Delivery Address")
    fun criacaoRecusada() {
        assertThat(customer).isNull()
    }

    @When("um segundo Delivery Address é registrado")
    fun segundoDeliveryAddressRegistrado() {
        customer!!.addDeliveryAddress(DeliveryAddress())
    }

    @Then("o Customer tem {int} Delivery Addresses")
    fun customerTemNAddresses(n: Int) {
        assertThat(customer!!.deliveryAddresses).hasSize(n)
    }

    @Then("o Default Delivery Address continua sendo o primeiro")
    fun defaultContinuaPrimeiro() {
        assertThat(customer!!.defaultDeliveryAddress).isEqualTo(firstAddress)
    }
}
