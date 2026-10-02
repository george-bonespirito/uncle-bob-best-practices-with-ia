package com.bonespirito.pricing.domain.policy

import com.bonespirito.pricing.domain.Sku
import com.bonespirito.pricing.domain.UnitPrice

/**
 * Pricing Enrichment (CONTEXT.md): dado um SKU, devolve o Unit Price e a
 * Currency. Price Not Found é a única falha de negócio — SKU sem preço não
 * recusa o pedido, a task fica travada até o preço existir (ADR order/0011).
 * Indisponibilidade do contexto nunca é Price Not Found e fica fora desta
 * assinatura. A assinatura não recebe cliente: o preço vale para todos
 * igualmente, por desenho.
 */
sealed interface PricingEnrichment {
    data class Enriched(val unitPrice: UnitPrice) : PricingEnrichment
    data object PriceNotFound : PricingEnrichment
}

fun enrichPrice(sku: Sku, lookup: (Sku) -> UnitPrice?): PricingEnrichment =
    lookup(sku)?.let { PricingEnrichment.Enriched(it) }
        ?: PricingEnrichment.PriceNotFound
