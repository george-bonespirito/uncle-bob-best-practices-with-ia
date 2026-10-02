package com.bonespirito.customers.domain

import com.bonespirito.customers.domain.policy.DocumentDigits
import com.bonespirito.customers.domain.policy.validateDocument

/**
 * Documento legal do Customer — CPF ou CNPJ (CONTEXT.md). O formato determina
 * o Customer Type e o dígito verificador é invariante: um Document inválido
 * não é representável — o construtor privado garante, e `of` recusa.
 *
 * Só dígitos: máscara (pontuação) não é decidida no glossário; entra aqui só
 * se o architect decidir.
 */
class Document private constructor(val digits: String) {

    /** Customer Type: derivado do Document, nunca informado à parte. */
    val type: CustomerType =
        if (digits.length == CPF_EXPECTED_LENGTH) CustomerType.INDIVIDUAL else CustomerType.COMPANY

    override fun equals(other: Any?): Boolean = other is Document && other.digits == digits
    override fun hashCode(): Int = digits.hashCode()
    override fun toString(): String = "Document($digits)"

    companion object {
        private const val CPF_EXPECTED_LENGTH = 11

        fun of(digits: String): Document =
            when (val result = validateDocument(digits)) {
                is DocumentDigits.Valid -> Document(result.digits)
                is DocumentDigits.Invalid ->
                    throw InvalidDocumentException(result.reason)
            }
    }
}

class InvalidDocumentException(val reason: String) : IllegalArgumentException("documento inválido: $reason")

/** Natureza do Customer — derivada do Document. */
enum class CustomerType { INDIVIDUAL, COMPANY }
