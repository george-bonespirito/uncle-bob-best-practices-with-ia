package com.bonespirito.products.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PhysicalAttributesTest {

    @Test
    fun `atributos nao negativos sao validos`() {
        val attrs = PhysicalAttributes(
            weight = BigDecimal("2.50"),
            length = BigDecimal("30"),
            width = BigDecimal("20"),
            height = BigDecimal("15"),
        )

        assertThat(attrs.weight).isEqualByComparingTo(BigDecimal("2.50"))
        assertThat(attrs.height).isEqualByComparingTo(BigDecimal("15"))
    }

    @Test
    fun `atributo negativo e recusa`() {
        assertThatThrownBy {
            PhysicalAttributes(
                weight = BigDecimal("-1"),
                length = BigDecimal("30"),
                width = BigDecimal("20"),
                height = BigDecimal("15"),
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("atributo físico inválido: negativo")
    }
}
