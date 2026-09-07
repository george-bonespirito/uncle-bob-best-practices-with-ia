package com.bonespirito.order.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

enum class OrderStatus {
    PENDING,
    PROCESSING,
    AWAITING_MASTER_DATA,
    INVOICING,
    COMPLETED,
    COMPENSATING,
    CANCELLED,
    COMPENSATION_FAILED,
}

@Embeddable
data class SourceOrderReference(
    val sourceSystemCode: String,
    val sourceOrderNumber: String,
)

@Entity
@Table(name = "orders")
class Order(
    @Id
    val id: UUID = UUID.randomUUID(),

    val sourceOrderReference: SourceOrderReference,
    val customerId: UUID,
    val orderCreatedAt: Instant,

    @Enumerated(EnumType.STRING)
    var status: OrderStatus = OrderStatus.PENDING,

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    val items: MutableList<OrderItem> = mutableListOf(),
) {
    @Version
    var version: Long = 0
}
