package com.bonespirito.products.domain

/**
 * Identidade do Product, emitida por este contexto e usada pelo mundo todo
 * pra referenciá-lo (CONTEXT.md). Sem código alternativo nem conversão.
 */
@JvmInline
value class Sku(val value: String)
