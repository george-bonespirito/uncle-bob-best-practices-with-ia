package com.bonespirito.products.domain

import java.math.BigDecimal

/**
 * Peso e dimensões do Product, obrigatórios no cadastro (CONTEXT.md): peso
 * restringe carga por limite de eixo, cubagem por espaço — um Product sem
 * eles é inútil pro Route. Nunca vão pro Product Snapshot: o Order não
 * roteiriza. Unidades não são decididas no glossário; a fatia modela a
 * obrigatoriedade, e valor negativo é recusa (não é atributo físico).
 */
data class PhysicalAttributes(
    val weight: BigDecimal,
    val length: BigDecimal,
    val width: BigDecimal,
    val height: BigDecimal,
) {
    init {
        require(
            listOf(weight, length, width, height).all { it >= BigDecimal.ZERO },
        ) { "atributo físico inválido: negativo" }
    }
}
