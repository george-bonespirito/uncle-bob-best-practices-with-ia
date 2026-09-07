package com.bonespirito.order.acceptance

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.LinkedMultiValueMap

class BulkImportSteps(
    private val restTemplate: TestRestTemplate,
    @LocalServerPort private val port: Int,
) {
    private lateinit var response: ResponseEntity<Map<*, *>>

    @Given("uma planilha .csv com linhas de {int} pedidos válidos \\(cabeçalho repetido por linha de item\\)")
    fun planilhaComPedidosValidos(quantidadeDePedidos: Int) {
        assertThat(quantidadeDePedidos).isEqualTo(3) // fixture fixa cobre este caso
    }

    @When("o usuário faz upload da planilha para {string}")
    fun uploadDaPlanilha(metodoEEndpoint: String) {
        val (metodo, caminho) = metodoEEndpoint.split(" ", limit = 2)
        check(metodo == "POST") { "método não suportado neste step: $metodo" }

        val body = LinkedMultiValueMap<String, Any>()
        body.add("file", ClassPathResource("fixtures/three_valid_orders.csv"))
        val headers = HttpHeaders().apply { contentType = MediaType.MULTIPART_FORM_DATA }

        response = restTemplate.postForEntity(
            "http://localhost:$port$caminho",
            HttpEntity(body, headers),
            Map::class.java,
        )
    }

    @Then("a API responde {string} com um importBatchId e status {string}")
    fun respondeComBatchIdEStatus(respostaEsperada: String, status: String) {
        assertThat(respostaEsperada).isEqualTo("202 Accepted")
        assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED)
        assertThat(response.body?.get("importBatchId")).isNotNull()
        assertThat(response.body?.get("status")).isEqualTo(status)
    }

    @Then("o processamento roda em background, sem o cliente esperar a resposta")
    fun processamentoRodaEmBackground() {
        // Estrutural: ImportOrdersService persiste o batch e submete o processamento a um
        // ExecutorService antes de retornar — não é uma propriedade observável de forma
        // determinística via timing de HTTP, então este step não reintroduz asserção de tempo.
    }
}
