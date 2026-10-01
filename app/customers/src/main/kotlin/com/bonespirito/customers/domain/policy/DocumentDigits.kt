package com.bonespirito.customers.domain.policy

/**
 * Validação de Document (CONTEXT.md: o dígito verificador é invariante, então
 * um Document inválido não é representável). Regra vive aqui — `domain.policy` —
 * e não depende de Spring nem de JPA (ADR order/0006, espelhada neste contexto).
 *
 * Só dígitos: aceitar ou não máscara (pontuação de CPF/CNPJ) não é decidido no
 * glossário; se precisar, sobe como blocked pro architect, não se inventa.
 */
sealed interface DocumentDigits {
    data class Valid(val digits: String) : DocumentDigits
    data class Invalid(val reason: String) : DocumentDigits
}

const val CPF_LENGTH = 11
const val CNPJ_LENGTH = 14

private const val MOD11 = 11
private const val REST_BELOW_MEANS_ZERO = 2
private const val MIN_WEIGHT = 2

private const val CPF_DV1_POSITION = CPF_LENGTH - 2
private const val CPF_DV2_POSITION = CPF_LENGTH - 1
private const val CNPJ_DV1_POSITION = CNPJ_LENGTH - 2
private const val CNPJ_DV2_POSITION = CNPJ_LENGTH - 1

private val CPF_DV1_WEIGHTS = (CPF_LENGTH - 1 downTo MIN_WEIGHT).toList()
private val CPF_DV2_WEIGHTS = (CPF_LENGTH downTo MIN_WEIGHT).toList()

// Pesos do algoritmo de DV de CNPJ (mod 11 com rotação) — são tabela da norma,
// não decisão local; suprimir é mais honesto que dar nome falso pra cada número.
@Suppress("MagicNumber")
private val CNPJ_DV1_WEIGHTS = listOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)

@Suppress("MagicNumber")
private val CNPJ_DV2_WEIGHTS = listOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)

fun validateDocument(digits: String): DocumentDigits = when {
    digits.length != CPF_LENGTH && digits.length != CNPJ_LENGTH ->
        DocumentDigits.Invalid("tamanho ${digits.length} fora de CPF ($CPF_LENGTH) ou CNPJ ($CNPJ_LENGTH)")
    digits.all { it == digits[0] } ->
        DocumentDigits.Invalid("dígito verificador inválido")
    digits.length == CPF_LENGTH && !validCpf(digits) ->
        DocumentDigits.Invalid("dígito verificador inválido")
    digits.length == CNPJ_LENGTH && !validCnpj(digits) ->
        DocumentDigits.Invalid("dígito verificador inválido")
    else -> DocumentDigits.Valid(digits)
}

private fun checkDigit(digits: String, weights: List<Int>): Int {
    val sum = weights.mapIndexed { i, w -> (digits[i] - '0') * w }.sum()
    val rest = sum % MOD11
    return if (rest < REST_BELOW_MEANS_ZERO) 0 else MOD11 - rest
}

private fun validCpf(digits: String): Boolean {
    val dv1 = checkDigit(digits, CPF_DV1_WEIGHTS)
    val dv2 = checkDigit(digits, CPF_DV2_WEIGHTS)
    return digits[CPF_DV1_POSITION] - '0' == dv1 && digits[CPF_DV2_POSITION] - '0' == dv2
}

private fun validCnpj(digits: String): Boolean {
    val dv1 = checkDigit(digits, CNPJ_DV1_WEIGHTS)
    val dv2 = checkDigit(digits, CNPJ_DV2_WEIGHTS)
    return digits[CNPJ_DV1_POSITION] - '0' == dv1 && digits[CNPJ_DV2_POSITION] - '0' == dv2
}
