package com.bonespirito.products.domain.policy

import com.bonespirito.products.domain.PhysicalAttributes
import com.bonespirito.products.domain.Product
import com.bonespirito.products.domain.ProductStatus
import com.bonespirito.products.domain.Sku
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ProductEnrichmentTest {

    private val sku = Sku("SKU-1")
    private val product = Product.create(
        Sku("SKU-1"), "Furadeira", "Furadeira 750W com maleta", "Ferramentas",
        PhysicalAttributes(BigDecimal("2.5"), BigDecimal("30"), BigDecimal("20"), BigDecimal("15")),
    )

    @Test
    fun `sku existente devolve snapshot com name category e status`() {
        val result = enrichProduct(sku) { product }

        assertThat(result).isEqualTo(
            ProductEnrichment.Enriched(
                ProductSnapshot("Furadeira", "Ferramentas", ProductStatus.ACTIVE),
            ),
        )
    }

    @Test
    fun `snapshot nao carrega description nem atributos fisicos`() {
        val snapshot = ProductSnapshot.from(product)

        assertThat(snapshot.name).isEqualTo("Furadeira")
        assertThat(snapshot.category).isEqualTo("Ferramentas")
        assertThat(snapshot).isEqualTo(
            ProductSnapshot("Furadeira", "Ferramentas", ProductStatus.ACTIVE),
        )
    }

    @Test
    fun `sku inexistente e product not found`() {
        val result = enrichProduct(sku) { null }

        assertThat(result).isEqualTo(ProductEnrichment.ProductNotFound)
    }

    @Test
    fun `inactive enriquece — nao vira product not found`() {
        val inactive = Product.create(
            Sku("SKU-2"), "Serra", "sem descrição", "Ferramentas",
            PhysicalAttributes(BigDecimal("5"), BigDecimal("40"), BigDecimal("30"), BigDecimal("20")),
        ).deactivate()

        val result = enrichProduct(Sku("SKU-2")) { inactive }

        assertThat(result).isEqualTo(
            ProductEnrichment.Enriched(ProductSnapshot("Serra", "Ferramentas", ProductStatus.INACTIVE)),
        )
    }
}
