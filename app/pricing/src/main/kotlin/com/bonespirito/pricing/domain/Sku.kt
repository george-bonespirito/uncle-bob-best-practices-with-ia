package com.bonespirito.pricing.domain

/**
 * Identidade do produto para este contexto, referenciada do Products
 * (CONTEXT.md): o Pricing responde quanto custa um SKU, sem hierarquia.
 */
@JvmInline
value class Sku(val value: String)
