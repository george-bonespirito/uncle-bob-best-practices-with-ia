package com.bonespirito.pricing.domain

import java.math.BigDecimal

/**
 * Preço de venda de um SKU, em moeda única, igual para todos os clientes
 * (CONTEXT.md): o preço é do produto, não da relação comercial. Zero é um
 * preço válido e não é ausência — Price Not Found é outra coisa, decidida
 * pelo enriquecimento, não pelo valor.
 */
data class UnitPrice(
    val amount: BigDecimal,
    val currency: Currency,
) {
    init {
        require(amount >= BigDecimal.ZERO) { "preço inválido: negativo" }
    }
}
