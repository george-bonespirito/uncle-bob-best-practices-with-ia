package com.bonespirito.products.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ProductTest {

    private fun attrs() = PhysicalAttributes(
        weight = BigDecimal("2.50"),
        length = BigDecimal("30"),
        width = BigDecimal("20"),
        height = BigDecimal("15"),
    )

    @Test
    fun `cadastro novo nasce ACTIVE`() {
        val product = Product.create(Sku("SKU-1"), "Furadeira", "com maleta", "Ferramentas", attrs())

        assertThat(product.status).isEqualTo(ProductStatus.ACTIVE)
        assertThat(product.sku).isEqualTo(Sku("SKU-1"))
    }

    @Test
    fun `INACTIVE existe com cadastro completo`() {
        val product = Product.create(
            Sku("SKU-1"), "Furadeira", "com maleta", "Ferramentas", attrs(),
        ).deactivate()

        assertThat(product.status).isEqualTo(ProductStatus.INACTIVE)
        assertThat(product.name).isEqualTo("Furadeira")
        assertThat(product.description).isEqualTo("com maleta")
    }

    @Test
    fun `sem physical attributes e recusa`() {
        assertThatThrownBy {
            Product.create(Sku("SKU-1"), "Furadeira", "com maleta", "Ferramentas", null)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Physical Attributes são obrigatórios")
    }

    @Test
    fun `name ausente e recusa`() {
        assertThatThrownBy {
            Product.create(Sku("SKU-1"), "", "com maleta", "Ferramentas", attrs())
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("name ausente")
    }

    @Test
    fun `category ausente e recusa`() {
        assertThatThrownBy {
            Product.create(Sku("SKU-1"), "Furadeira", "com maleta", "", attrs())
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("category ausente")
    }

    @Test
    fun `name e description sao campos distintos`() {
        val product = Product.create(
            Sku("SKU-1"), "Furadeira", "Furadeira 750W com maleta", "Ferramentas", attrs(),
        )

        assertThat(product.name).isEqualTo("Furadeira")
        assertThat(product.description).isEqualTo("Furadeira 750W com maleta")
    }
}
