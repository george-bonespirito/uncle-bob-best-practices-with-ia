package com.bonespirito.order.domain.policy

import com.bonespirito.order.domain.FulfillmentStatus.FULFILLED
import com.bonespirito.order.domain.FulfillmentStatus.NOT_FULFILLED
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderTotalTest {

    @Test
    fun `order sem itens tem total zero`() {
        assertThat(orderTotal(emptyList())).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `total e a soma de unit_price vezes quantity dos itens FULFILLED`() {
        val items = listOf(
            PricedItem(BigDecimal("10.00"), 2, FULFILLED),
            PricedItem(BigDecimal("5.50"), 3, FULFILLED),
        )

        assertThat(orderTotal(items)).isEqualByComparingTo(BigDecimal("36.50"))
    }

    @Test
    fun `item NOT_FULFILLED fica fora do total`() {
        val items = listOf(
            PricedItem(BigDecimal("10.00"), 1, FULFILLED),
            PricedItem(BigDecimal("20.00"), 1, NOT_FULFILLED),
            PricedItem(BigDecimal("30.00"), 1, FULFILLED),
        )

        assertThat(orderTotal(items)).isEqualByComparingTo(BigDecimal("40.00"))
    }

    @Test
    fun `order sem nenhum item FULFILLED tem total zero`() {
        val items = listOf(
            PricedItem(BigDecimal("10.00"), 1, NOT_FULFILLED),
            PricedItem(BigDecimal("20.00"), 4, NOT_FULFILLED),
        )

        assertThat(orderTotal(items)).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `preco zero e preco - o item entra no total contribuindo zero`() {
        val items = listOf(
            PricedItem(BigDecimal("0.00"), 5, FULFILLED),
            PricedItem(BigDecimal("12.00"), 1, FULFILLED),
        )

        assertThat(orderTotal(items)).isEqualByComparingTo(BigDecimal("12.00"))
    }

    @Test
    fun `total e derivado - mudar o status do item muda o total sem passo de recalculo`() {
        val itens = listOf(
            PricedItem(BigDecimal("10.00"), 1, FULFILLED),
            PricedItem(BigDecimal("20.00"), 1, FULFILLED),
            PricedItem(BigDecimal("30.00"), 1, FULFILLED),
        )
        assertThat(orderTotal(itens)).isEqualByComparingTo(BigDecimal("60.00"))

        val depoisDaInvoiceGeneration = itens.mapIndexed { i, item ->
            if (i == 2) item.copy(fulfillment = NOT_FULFILLED) else item
        }

        assertThat(orderTotal(depoisDaInvoiceGeneration)).isEqualByComparingTo(BigDecimal("30.00"))
    }
}
