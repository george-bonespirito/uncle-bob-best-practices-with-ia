package com.bonespirito.customers.domain

import java.util.UUID

/** Identidade do Customer, emitida por este contexto (CONTEXT.md). */
@JvmInline
value class CustomerId(val value: UUID = UUID.randomUUID())

/**
 * Endereço onde o pedido do Customer pode ser entregue. Entidade filha do
 * agregado Customer — nunca existe fora dele.
 *
 * Conteúdo postal (CEP, logradouro, …) não é decidido no glossário: esta
 * fatia modela só a identidade e a regra de Default. Conteúdo sobe como
 * blocked pro architect quando o fluxo precisar.
 */
class DeliveryAddress(val id: UUID = UUID.randomUUID()) {
    override fun equals(other: Any?): Boolean = other is DeliveryAddress && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

/**
 * Aggregate root do contexto. Só é válido com pelo menos um Delivery Address,
 * e exatamente um é o Default Delivery Address — o primeiro cadastrado vira o
 * default, e o Customer nunca fica sem um (CONTEXT.md). Sem WhatsApp Phone
 * nesta fatia: a única consumidora (Lookup pelo MS Notifications) é fluxo
 * futuro, fora do despacho.
 */
class Customer private constructor(
    val id: CustomerId,
    val name: String,
    val document: Document,
    firstAddress: DeliveryAddress,
) {

    private val addressList = mutableListOf(firstAddress)

    /** Todos os Delivery Addresses; o primeiro elemento é o Default. */
    val deliveryAddresses: List<DeliveryAddress>
        get() = addressList.toList()

    /** O Default Delivery Address — primeiro cadastrado; nunca ausente. */
    val defaultDeliveryAddress: DeliveryAddress
        get() = addressList.first()

    fun addDeliveryAddress(address: DeliveryAddress) {
        addressList.add(address)
    }

    companion object {
        /**
         * Customer só existe com pelo menos um Delivery Address (CONTEXT.md).
         * `firstAddress` nullable só pra exercitar de fora o cenário "sem nenhum":
         * quem chama sem endereço recebe a recusa, não um Customer inválido.
         */
        fun create(name: String, document: Document, firstAddress: DeliveryAddress?): Customer {
            requireNotNull(firstAddress) { "Customer precisa de pelo menos um Delivery Address" }
            require(name.isNotBlank()) { "name ausente" }
            return Customer(CustomerId(), name, document, firstAddress)
        }
    }
}
