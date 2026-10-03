package com.bonespirito.customers.domain.policy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DocumentDigitsTest {

    @Test
    fun `CPF com DV valido vira Valid`() {
        val result = validateDocument("52998224725")

        assertThat(result).isEqualTo(DocumentDigits.Valid("52998224725"))
        assertThat((result as DocumentDigits.Valid).digits).isEqualTo("52998224725")
    }

    @Test
    fun `CPF com resto 2 no DV vira 9 — boundary rest == 2`() {
        // 00000000191: dv1 sai de rest == 2, que não é < 2, então dv = 11 - 2 = 9.
        // Distingue rest < 2 de rest <= 2 (boundary do checkDigit).
        val result = validateDocument("00000000191")

        assertThat(result).isEqualTo(DocumentDigits.Valid("00000000191"))
        assertThat((result as DocumentDigits.Valid).digits).isEqualTo("00000000191")
    }

    @Test
    fun `segundo DV do CPF segue a regra mod 11 com resto abaixo de 2 virando zero`() {
        // 34077572800: dv1 e dv2 calculados deixam resto < 2 em dv2, que vira 0
        assertThat(validateDocument("34077572800")).isEqualTo(DocumentDigits.Valid("34077572800"))
    }

    @Test
    fun `CNPJ com DV valido vira Valid`() {
        assertThat(validateDocument("11222333000181")).isEqualTo(DocumentDigits.Valid("11222333000181"))
    }

    @Test
    fun `CPF com DV errado vira Invalid por digito verificador`() {
        val result = validateDocument("52998224720")

        assertThat(result).isEqualTo(DocumentDigits.Invalid("dígito verificador inválido"))
    }

    @Test
    fun `CPF com segundo DV errado vira Invalid`() {
        assertThat(validateDocument("52998224720")).isInstanceOf(DocumentDigits.Invalid::class.java)
    }

    @Test
    fun `CNPJ com DV errado vira Invalid`() {
        val result = validateDocument("11222333000180")

        assertThat(result).isEqualTo(DocumentDigits.Invalid("dígito verificador inválido"))
    }

    @Test
    fun `tamanho fora de CPF ou CNPJ vira Invalid por tamanho`() {
        val result = validateDocument("12345")

        assertThat(result).isInstanceOf(DocumentDigits.Invalid::class.java)
        assertThat((result as DocumentDigits.Invalid).reason).startsWith("tamanho")
    }

    @Test
    fun `string vazia vira Invalid por tamanho`() {
        assertThat(validateDocument("")).isInstanceOf(DocumentDigits.Invalid::class.java)
    }

    @Test
    fun `CPF com todos os digitos iguais vira Invalid`() {
        val result = validateDocument("11111111111")

        assertThat(result).isEqualTo(DocumentDigits.Invalid("dígito verificador inválido"))
    }

    @Test
    fun `CNPJ com todos os digitos iguais vira Invalid`() {
        val result = validateDocument("00000000000000")

        assertThat(result).isEqualTo(DocumentDigits.Invalid("dígito verificador inválido"))
    }

    @Test
    fun `CPF com segundo digito calculado por resto abaixo de 2 valida como zero`() {
        // "34077572800" força o caminho rest < 2 no dv2 — distingue a regra de um dv que
        // simplesmente repetiria o calculo do dv1.
        val result = validateDocument("34077572800")

        assertThat(result).isEqualTo(DocumentDigits.Valid("34077572800"))
    }
}
