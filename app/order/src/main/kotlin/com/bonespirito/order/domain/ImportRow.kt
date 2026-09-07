package com.bonespirito.order.domain

import java.time.Instant
import java.util.UUID

data class ImportRow(
    val sourceSystemCode: String,
    val sourceOrderNumber: String,
    val customerId: UUID,
    val orderCreatedAt: Instant,
    val sku: String,
    val quantity: Int,
)
