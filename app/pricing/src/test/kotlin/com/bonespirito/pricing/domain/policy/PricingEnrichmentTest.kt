package com.bonespirito.pricing.domain.policy

import com.bonespirito.pricing.domain.Currency
import com.bonespirito.pricing.domain.Sku
import com.bonespirito.pricing.domain.UnitPrice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PricingEnrichmentTest {

    private val sku = Sku("SKU-1")
    private val price = UnitPrice(BigDecimal("10.00"), Currency("BRL"))

    @Test
    fun `sku com preco devolve unit price e currency`() {
        val result = enrichPrice(sku) { price }

        assertThat(result).isEqualTo(PricingEnrichment.Enriched(price))
    }

    @Test
    fun `sku sem preco e price not found`() {
        val result = enrichPrice(sku) { null }

        assertThat(result).isEqualTo(PricingEnrichment.PriceNotFound)
    }

    @Test
    fun `preco zero enriquece, nao vira price not found`() {
        val zero = UnitPrice(BigDecimal.ZERO, Currency("BRL"))

        val result = enrichPrice(sku) { zero }

        assertThat(result).isEqualTo(PricingEnrichment.Enriched(zero))
    }

    @Test
    fun `assinatura nao recebe cliente — preco e do produto`() {
        val lookup: (Sku) -> UnitPrice? = { price }

        assertThat(enrichPrice(sku, lookup)).isEqualTo(PricingEnrichment.Enriched(price))
    }
}
