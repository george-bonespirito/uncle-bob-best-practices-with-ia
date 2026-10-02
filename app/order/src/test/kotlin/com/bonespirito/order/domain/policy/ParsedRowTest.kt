package com.bonespirito.order.domain.policy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ParsedRowTest {

    private val validColumns = listOf(
        "ERP-SAP",
        "PO-001",
        "11111111-1111-1111-1111-111111111111",
        "2026-09-07T10:00:00Z",
        "SKU-1",
        "2",
    )

    @Test
    fun `linha com todos os campos valida vira ParsedRow Valid`() {
        val result = validateRow(1, validColumns)

        assertThat(result).isInstanceOf(ParsedRow.Valid::class.java)
        val numberedRow = (result as ParsedRow.Valid).numberedRow
        assertThat(numberedRow.rowNumber).isEqualTo(1)
        assertThat(numberedRow.row.sourceOrderNumber).isEqualTo("PO-001")
        assertThat(numberedRow.row.quantity).isEqualTo(2)
    }

    @Test
    fun `linha com menos colunas do que o esperado trata as que faltam como ausentes`() {
        val result = validateRow(1, emptyList())

        assertThat(result).isEqualTo(ParsedRow.Invalid(1, "source_system_code ausente"))
    }

    // A última coluna presente é a fronteira de getOrElse: uma linha truncada logo depois
    // dela precisa apontar a próxima coluna ausente, não a última que existe.
    @Test
    fun `linha truncada apos source_system_code aponta source_order_number ausente`() {
        val result = validateRow(1, validColumns.take(1))

        assertThat(result).isEqualTo(ParsedRow.Invalid(1, "source_order_number ausente"))
    }

    @Test
    fun `linha truncada apos source_order_number aponta customer_id ausente`() {
        val result = validateRow(1, validColumns.take(2))

        assertThat(result).isEqualTo(ParsedRow.Invalid(1, "customer_id ausente"))
    }

    @Test
    fun `linha truncada apos customer_id aponta sku ausente`() {
        val result = validateRow(1, validColumns.take(3))

        assertThat(result).isEqualTo(ParsedRow.Invalid(1, "sku ausente"))
    }

    @Test
    fun `linha truncada apos sku trata quantity ausente como formato invalido`() {
        val result = validateRow(7, validColumns.take(5))

        assertThat(result).isInstanceOf(ParsedRow.Invalid::class.java)
        val invalid = result as ParsedRow.Invalid
        assertThat(invalid.rowNumber).isEqualTo(7)
        assertThat(invalid.reason).startsWith("linha em formato inválido")
    }

    @Test
    fun `source_system_code ausente vira ParsedRow Invalid`() {
        val columns = validColumns.toMutableList().apply { this[0] = "" }

        val result = validateRow(2, columns)

        assertThat(result).isEqualTo(ParsedRow.Invalid(2, "source_system_code ausente"))
    }

    @Test
    fun `customer_id ausente vira ParsedRow Invalid`() {
        val columns = validColumns.toMutableList().apply { this[2] = "" }

        val result = validateRow(3, columns)

        assertThat(result).isEqualTo(ParsedRow.Invalid(3, "customer_id ausente"))
    }

    @Test
    fun `sku ausente vira ParsedRow Invalid`() {
        val columns = validColumns.toMutableList().apply { this[4] = "" }

        val result = validateRow(4, columns)

        assertThat(result).isEqualTo(ParsedRow.Invalid(4, "sku ausente"))
    }

    @Test
    fun `customer_id em formato invalido vira ParsedRow Invalid com motivo de formato`() {
        val columns = validColumns.toMutableList().apply { this[2] = "nao-e-um-uuid" }

        val result = validateRow(5, columns)

        assertThat(result).isInstanceOf(ParsedRow.Invalid::class.java)
        assertThat((result as ParsedRow.Invalid).reason).startsWith("linha em formato inválido")
    }

    @Test
    fun `quantity nao numerico vira ParsedRow Invalid com motivo de formato`() {
        val columns = validColumns.toMutableList().apply { this[5] = "duas" }

        val result = validateRow(6, columns)

        assertThat(result).isInstanceOf(ParsedRow.Invalid::class.java)
        assertThat((result as ParsedRow.Invalid).reason).startsWith("linha em formato inválido")
    }
}
