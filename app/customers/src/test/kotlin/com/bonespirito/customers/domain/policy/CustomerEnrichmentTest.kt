package com.bonespirito.customers.domain.policy

import com.bonespirito.customers.domain.Customer
import com.bonespirito.customers.domain.DeliveryAddress
import com.bonespirito.customers.domain.Document
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CustomerEnrichmentTest {

    private val customer = Customer.create(
        name = "Distribuidora Horizonte",
        document = Document.of("11222333000181"),
        firstAddress = DeliveryAddress(),
    )

    @Test
    fun `Customer existente devolve Snapshot com Name Document e Default Delivery Address`() {
        val result = enrichCustomer(customer)

        assertThat(result).isInstanceOf(CustomerEnrichment.Enriched::class.java)
        val snapshot = (result as CustomerEnrichment.Enriched).snapshot
        assertThat(snapshot.name).isEqualTo("Distribuidora Horizonte")
        assertThat(snapshot.document).isEqualTo(customer.document)
        assertThat(snapshot.defaultDeliveryAddress).isEqualTo(customer.defaultDeliveryAddress)
    }

    @Test
    fun `Customer inexistente falha como Customer Not Found`() {
        assertThat(enrichCustomer(null)).isEqualTo(CustomerEnrichment.CustomerNotFound)
    }
}
