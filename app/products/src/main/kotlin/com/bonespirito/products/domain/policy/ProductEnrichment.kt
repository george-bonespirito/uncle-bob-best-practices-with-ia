package com.bonespirito.products.domain.policy

import com.bonespirito.products.domain.Product
import com.bonespirito.products.domain.ProductStatus
import com.bonespirito.products.domain.Sku

/**
 * Product Enrichment (CONTEXT.md): dado um sku, devolve Name, Category e o
 * Product Status — a única coisa que este contexto faz pelo Order. Product
 * Not Found é o sku não existir; não recusa o pedido, a task fica travada
 * (ADR order/0011). INACTIVE não é Not Found: produto que existe e não é
 * vendável — desfechos opostos no Order. Indisponibilidade do contexto fica
 * fora desta assinatura.
 */
sealed interface ProductEnrichment {
    data class Enriched(val snapshot: ProductSnapshot) : ProductEnrichment
    data object ProductNotFound : ProductEnrichment
}

fun enrichProduct(sku: Sku, lookup: (Sku) -> Product?): ProductEnrichment =
    lookup(sku)?.let { ProductEnrichment.Enriched(ProductSnapshot.from(it)) }
        ?: ProductEnrichment.ProductNotFound

/**
 * O que o Order grava como Snapshot: Name, Category e Product Status.
 * Description e Physical Attributes ficam aqui — a linha de pedido mostra
 * Name, e o Order não roteiriza.
 */
data class ProductSnapshot(
    val name: String,
    val category: String,
    val status: ProductStatus,
) {
    companion object {
        fun from(product: Product) = ProductSnapshot(
            name = product.name,
            category = product.category,
            status = product.status,
        )
    }
}
