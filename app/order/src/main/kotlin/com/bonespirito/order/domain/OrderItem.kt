package com.bonespirito.order.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "order_items")
class OrderItem(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne
    @JoinColumn(name = "order_id")
    val order: Order,

    val sku: String,
    val quantity: Int,
)
