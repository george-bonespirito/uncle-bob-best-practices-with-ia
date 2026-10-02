package com.bonespirito.products.domain

/**
 * Situação do Product no catálogo (CONTEXT.md). INACTIVE não é excluído: o
 * SKU existe, tem cadastro completo e histórico, e não é mais vendável.
 * Diferente de Product Not Found, que é o SKU não existir — desfechos
 * opostos no Order.
 */
enum class ProductStatus {
    ACTIVE,
    INACTIVE,
}
