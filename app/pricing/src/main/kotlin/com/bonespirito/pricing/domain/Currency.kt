package com.bonespirito.pricing.domain

/**
 * Moeda do Unit Price (CONTEXT.md): sempre a mesma para o sistema inteiro.
 * O campo existe para deixar a moeda explícita no dado, não para suportar
 * mistura — não há taxa de câmbio em lugar nenhum do desenho. O código
 * concreto é dado do catálogo futuro; esta fatia modela só a explicitação.
 */
data class Currency(val code: String)
