package com.bonespirito.customers.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CustomerTest {

    private val companyDocument = Document.of("11222333000181")

    @Test
    fun `Document deriva Customer Type a partir do formato`() {
        assertThat(Document.of("52998224725").type).isEqualTo(CustomerType.INDIVIDUAL)
        assertThat(companyDocument.type).isEqualTo(CustomerType.COMPANY)
    }

    @Test
    fun `Document com digito verificador invalido nao e representavel`() {
        assertThatThrownBy { Document.of("52998224720") }
            .isInstanceOf(InvalidDocumentException::class.java)
            .hasMessageContaining("dígito verificador")
    }

    @Test
    fun `Document com tamanho fora de CPF ou CNPJ nao e representavel`() {
        assertThatThrownBy { Document.of("12345") }
            .isInstanceOf(InvalidDocumentException::class.java)
            .hasMessageContaining("tamanho")
    }

    @Test
    fun `Customer precisa de pelo menos um Delivery Address`() {
        assertThatThrownBy {
            Customer.create("Maria Silva", Document.of("52998224725"), firstAddress = null)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Delivery Address")
    }

    @Test
    fun `primeiro Delivery Address vira o Default`() {
        val first = DeliveryAddress()

        val customer = Customer.create("Distribuidora Horizonte", companyDocument, first)

        assertThat(customer.deliveryAddresses).containsExactly(first)
        assertThat(customer.defaultDeliveryAddress).isEqualTo(first)
    }

    @Test
    fun `segundo Delivery Address nao muda o Default`() {
        val first = DeliveryAddress()
        val customer = Customer.create("Distribuidora Horizonte", companyDocument, first)
        val second = DeliveryAddress()

        customer.addDeliveryAddress(second)

        assertThat(customer.deliveryAddresses).containsExactly(first, second)
        assertThat(customer.defaultDeliveryAddress).isEqualTo(first)
    }

    @Test
    fun `lista de Delivery Addresses exposta e uma copia — nao da pra burlar a regra do default`() {
        val first = DeliveryAddress()
        val customer = Customer.create("Distribuidora Horizonte", companyDocument, first)

        assertThat(customer.deliveryAddresses).isNotSameAs(customer.deliveryAddresses)
        assertThat(customer.deliveryAddresses).first().isEqualTo(first)
    }
}
