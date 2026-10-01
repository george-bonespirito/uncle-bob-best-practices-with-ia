package com.bonespirito.customers.domain.policy

import com.bonespirito.customers.domain.Customer

/**
 * Customer Enrichment (CONTEXT.md): dado um customer_id resolvido para um
 * Customer (ou não), devolve o que o Order grava como Snapshot. Customer Not
 * Found é a única falha de negócio — indisponibilidade do contexto é outra
 * coisa e fica fora desta assinatura (o consumidor precisa distingui-las).
 */
sealed interface CustomerEnrichment {
    data class Enriched(val snapshot: CustomerSnapshot) : CustomerEnrichment
    data object CustomerNotFound : CustomerEnrichment
}

fun enrichCustomer(customer: Customer?): CustomerEnrichment =
    customer?.let { CustomerEnrichment.Enriched(CustomerSnapshot.from(it)) }
        ?: CustomerEnrichment.CustomerNotFound

/** O que o Order grava como Snapshot: Name, Document e Default Delivery Address. */
data class CustomerSnapshot(
    val name: String,
    val document: com.bonespirito.customers.domain.Document,
    val defaultDeliveryAddress: com.bonespirito.customers.domain.DeliveryAddress,
) {
    companion object {
        fun from(customer: Customer) = CustomerSnapshot(
            name = customer.name,
            document = customer.document,
            defaultDeliveryAddress = customer.defaultDeliveryAddress,
        )
    }
}
