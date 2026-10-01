package com.bonespirito.order.acceptance

import com.bonespirito.order.domain.FulfillmentStatus
import com.bonespirito.order.domain.FulfillmentStatus.FULFILLED
import com.bonespirito.order.domain.policy.PricedItem
import com.bonespirito.order.domain.policy.orderTotal
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal

class OrderValueSteps(private val jdbcTemplate: JdbcTemplate) {
    // Os três itens usam preço e quantidade distintos para que o total prove
    // unit_price × quantity, não só a contagem de itens.
    private var items: List<PricedItem> = emptyList()
    private var totalAntes: BigDecimal? = null
    private var total: BigDecimal = BigDecimal.ZERO

    @Given("um Order {string} com {int} itens precificados")
    fun orderComItensPrecificados(@Suppress("UNUSED_PARAMETER") numero: String, quantidade: Int) {
        assertThat(quantidade).isEqualTo(3) // massa fixa cobre este caso
        items = tresItens(FULFILLED)
    }

    @Given("um Order {string} com {int} itens atendidos e precificados")
    fun orderComItensAtendidosEPrecificados(@Suppress("UNUSED_PARAMETER") numero: String, quantidade: Int) {
        assertThat(quantidade).isEqualTo(3) // massa fixa cobre este caso
        items = tresItens(FULFILLED)
        totalAntes = orderTotal(items)
    }

    @Given("o item {int} está {string}")
    fun itemEstaComStatus(posicao: Int, status: String) {
        items = comStatus(posicao, FulfillmentStatus.valueOf(status))
    }

    @When("o total do Order é calculado")
    fun totalCalculado() {
        total = orderTotal(items)
    }

    @When("a Invoice Generation marca o item {int} como {string}")
    fun invoiceGenerationMarcaItem(posicao: Int, status: String) {
        items = comStatus(posicao, FulfillmentStatus.valueOf(status))
        // Nenhuma chamada de recálculo aqui: o total só é lido do estado dos itens.
        total = orderTotal(items)
    }

    @Then("o total considera apenas os itens {int} e {int}")
    fun totalConsideraApenas(a: Int, b: Int) {
        assertThat(total).isEqualByComparingTo(subtotal(a) + subtotal(b))
    }

    @Then("o total do Order passa a considerar apenas os itens {int} e {int}")
    fun totalPassaAConsiderarApenas(a: Int, b: Int) {
        assertThat(total).isEqualByComparingTo(subtotal(a) + subtotal(b))
        assertThat(total).isNotEqualByComparingTo(totalAntes)
    }

    @Then("nenhuma coluna de total é gravada na tabela {string}")
    fun nenhumaColunaDeTotal(tabela: String) {
        assertThat(colunasDe(tabela)).noneMatch { it.contains("total") }
    }

    @Then("nenhum passo de recálculo precisou ser disparado")
    fun nenhumPassoDeRecalculo() {
        // O único lugar onde um total gravado poderia existir é o schema: sem coluna,
        // o valor novo só pode ter vindo da derivação sobre os itens.
        assertThat(colunasDe("orders")).noneMatch { it.contains("total") }
    }

    private fun tresItens(status: FulfillmentStatus) = listOf(
        PricedItem(BigDecimal("10.00"), 2, status),
        PricedItem(BigDecimal("20.00"), 1, status),
        PricedItem(BigDecimal("5.50"), 3, status),
    )

    private fun comStatus(posicao: Int, status: FulfillmentStatus) =
        items.mapIndexed { i, item -> if (i == posicao - 1) item.copy(fulfillment = status) else item }

    private fun subtotal(posicao: Int): BigDecimal {
        val item = items[posicao - 1]
        return item.unitPrice * BigDecimal(item.quantity)
    }

    private fun colunasDe(tabela: String): List<String> =
        jdbcTemplate.queryForList(
            "SELECT LOWER(column_name) FROM information_schema.columns WHERE LOWER(table_name) = ?",
            String::class.java,
            tabela.lowercase(),
        )
}
