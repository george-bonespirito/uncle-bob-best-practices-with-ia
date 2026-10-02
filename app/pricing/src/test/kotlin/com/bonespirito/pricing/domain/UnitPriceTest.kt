package com.bonespirito.pricing.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class UnitPriceTest {

    private val currency = Currency("BRL")

    @Test
    fun `preco zero e valido`() {
        val price = UnitPrice(BigDecimal.ZERO, currency)

        assertThat(price.amount).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(price.currency).isEqualTo(currency)
    }

    @Test
    fun `preco positivo e valido`() {
        val price = UnitPrice(BigDecimal("10.00"), currency)

        assertThat(price.amount).isEqualByComparingTo(BigDecimal("10.00"))
    }

    @Test
    fun `preco negativo nao existe`() {
        assertThatThrownBy { UnitPrice(BigDecimal("-1.00"), currency) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("preço inválido: negativo")
    }

    @Test
    fun `moeda e explicita no dado`() {
        val price = UnitPrice(BigDecimal("10.00"), Currency("BRL"))

        assertThat(price.currency.code).isEqualTo("BRL")
    }
}
