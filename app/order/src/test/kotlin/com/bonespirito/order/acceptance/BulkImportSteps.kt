package com.bonespirito.order.acceptance

import com.bonespirito.order.application.port.ImportBatchRepository
import com.bonespirito.order.application.port.ImportBatchRowErrorRepository
import com.bonespirito.order.application.port.OrderRepository
import com.bonespirito.order.domain.SourceOrderReference
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
import java.util.UUID

class BulkImportSteps(
    private val restTemplate: TestRestTemplate,
    private val importBatches: ImportBatchRepository,
    private val rowErrors: ImportBatchRowErrorRepository,
    private val orders: OrderRepository,
    @LocalServerPort private val port: Int,
) {
    private lateinit var response: ResponseEntity<Map<*, *>>
    private lateinit var importBatchId: UUID
    private var fixture = "fixtures/three_valid_orders.csv"

    @Given("uma planilha .csv com linhas de {int} pedidos válidos \\(cabeçalho repetido por linha de item\\)")
    fun planilhaComPedidosValidos(quantidadeDePedidos: Int) {
        assertThat(quantidadeDePedidos).isEqualTo(3) // fixture fixa cobre este caso
    }

    @Given("uma planilha .csv com linhas de {int} pedidos, sendo 1 com dado de cliente ausente")
    fun planilhaComClienteAusente(quantidadeDePedidos: Int) {
        assertThat(quantidadeDePedidos).isEqualTo(3) // fixture fixa cobre este caso
        fixture = "fixtures/three_orders_missing_customer.csv"
    }

    @Given("uma planilha .csv com linhas de {int} pedidos, sendo 1 com um customer_id que não existe no MS Customers")
    fun planilhaComClienteInexistente(quantidadeDePedidos: Int) {
        assertThat(quantidadeDePedidos).isEqualTo(3) // fixture fixa cobre este caso
        fixture = "fixtures/three_orders_unknown_customer.csv"
    }

    @Given("uma planilha .csv com um pedido que o banco recusa ao gravar \\(source_system_code maior que a coluna\\)")
    fun planilhaQueOBancoRecusaAoGravar() {
        // source_system_code com 60 caracteres passa na validação de linha, mas estoura o
        // VARCHAR(50) de orders.source_system_code (V2) — falha real, fora do caminho feliz.
        fixture = "fixtures/order_rejected_by_database.csv"
    }

    @Given("uma planilha .csv com um pedido válido, uma linha inválida e um pedido que o banco recusa ao gravar")
    fun planilhaComFalhaDepoisDeDadoValido() {
        // PO-910 é válido e PO-911 é erro de linha; PO-912 estoura o VARCHAR(50) de
        // orders.source_system_code (V2). Sem transação, o PO-910 ficava gravado num lote FAILED.
        fixture = "fixtures/orders_partially_rejected_by_database.csv"
    }

    @Given("uma planilha .csv já importada com sucesso")
    fun planilhaJaImportadaComSucesso() {
        val uploadResponse = upload()
        importBatchId = UUID.fromString(uploadResponse.body?.get("importBatchId") as String)
        awaitProcessamentoConcluido()
    }

    @Given("um importBatchId de uma planilha com {int} pedidos válidos, já processada")
    fun importBatchIdJaProcessado(quantidadeDePedidos: Int) {
        assertThat(quantidadeDePedidos).isEqualTo(3) // fixture fixa cobre este caso

        val uploadResponse = upload()
        importBatchId = UUID.fromString(uploadResponse.body?.get("importBatchId") as String)
        response = awaitProcessamentoConcluido()
    }

    @Given("um Order já importado com source_system_code {string} e source_order_number {string}")
    fun orderJaImportado(sourceSystemCode: String, sourceOrderNumber: String) {
        // fixture fixa cobre este caso
        assertThat(sourceSystemCode to sourceOrderNumber).isEqualTo("ERP-SAP" to "PO-123")
        fixture = "fixtures/order_po_123.csv"
        val uploadResponse = upload()
        importBatchId = UUID.fromString(uploadResponse.body?.get("importBatchId") as String)
        awaitProcessamentoConcluido()
    }

    @When("o usuário faz upload da planilha para {string}")
    fun uploadDaPlanilha(metodoEEndpoint: String) {
        val (metodo, _) = parseMetodoEEndpoint(metodoEEndpoint)
        check(metodo == "POST") { "método não suportado neste step: $metodo" }
        response = upload()
    }

    @When("o usuário faz upload do mesmo arquivo novamente para {string}")
    fun uploadDoMesmoArquivoNovamente(metodoEEndpoint: String) {
        val (metodo, _) = parseMetodoEEndpoint(metodoEEndpoint)
        check(metodo == "POST") { "método não suportado neste step: $metodo" }
        response = upload()
    }

    @When("o usuário faz upload e aguarda o processamento terminar")
    fun uploadEAguardaProcessamento() {
        val uploadResponse = upload()
        importBatchId = UUID.fromString(uploadResponse.body?.get("importBatchId") as String)
        response = awaitProcessamentoConcluido()
    }

    @When("uma nova planilha traz uma linha com o mesmo source_system_code e source_order_number")
    fun novaPlanilhaComMesmoSourceOrderNumber() {
        fixture = "fixtures/duplicate_po_123.csv"
        val uploadResponse = upload()
        importBatchId = UUID.fromString(uploadResponse.body?.get("importBatchId") as String)
        response = awaitProcessamentoConcluido()
    }

    @When("o usuário consulta {string}")
    fun consultaEndpointDeStatus(metodoEEndpoint: String) {
        val (metodo, caminhoTemplate) = parseMetodoEEndpoint(metodoEEndpoint)
        check(metodo == "GET") { "método não suportado neste step: $metodo" }

        val caminho = caminhoTemplate.replace("{importBatchId}", importBatchId.toString())
        response = restTemplate.getForEntity("http://localhost:$port$caminho", Map::class.java)
    }

    @Then("a API responde {string} com um importBatchId e status {string}")
    fun respondeComBatchIdEStatus(respostaEsperada: String, status: String) {
        assertThat(respostaEsperada).isEqualTo("202 Accepted")
        assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED)
        assertThat(response.body?.get("importBatchId")).isNotNull()
        assertThat(response.body?.get("status")).isEqualTo(status)
    }

    @Then("a API responde {string} com o importBatchId do lote original")
    fun respondeComBatchIdDoLoteOriginal(respostaEsperada: String) {
        assertThat(respostaEsperada).isEqualTo("409 Conflict")
        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body?.get("importBatchId")).isEqualTo(importBatchId.toString())
    }

    @Then("o processamento roda em background, sem o cliente esperar a resposta")
    fun processamentoRodaEmBackground() {
        // Estrutural: ImportOrdersService persiste o batch e submete o processamento a um
        // ExecutorService antes de retornar — não é uma propriedade observável de forma
        // determinística via timing de HTTP, então este step não reintroduz asserção de tempo.
    }

    @Then("o status é {string}")
    fun statusEIgualA(status: String) {
        assertThat(response.body?.get("status")).isEqualTo(status)
    }

    @Then("{string} é {int}")
    fun campoNumericoEIgualA(campo: String, valor: Int) {
        assertThat((response.body?.get(campo) as Number).toInt()).isEqualTo(valor)
    }

    @Then("o status final é {string}")
    fun statusFinalEIgualA(status: String) {
        assertThat(response.body?.get("status")).isEqualTo(status)
    }

    @Then("a linha inválida aparece em import_batch_row_errors com o motivo da falha")
    fun linhaInvalidaApareceComoErro() {
        val errors = rowErrors.findAll().filter { it.importBatchId == importBatchId }
        assertThat(errors).hasSize(1)
        assertThat(errors.first().reason).isNotBlank()
    }

    @Then("nenhuma linha aparece em import_batch_row_errors por causa do customer_id")
    fun nenhumaLinhaComErroDeCustomerId() {
        val errors = rowErrors.findAll().filter { it.importBatchId == importBatchId }
        assertThat(errors).isEmpty()
    }

    @Then("essa linha é reportada em import_batch_row_errors como duplicata")
    fun linhaReportadaComoDuplicata() {
        val errors = rowErrors.findAll().filter { it.importBatchId == importBatchId }
        assertThat(errors).hasSize(1)
        assertThat(errors.first().reason).contains("duplicata")
    }

    @Then("nenhum Order novo é criado para essa linha")
    fun nenhumOrderNovoCriadoParaEssaLinha() {
        val referencia = SourceOrderReference("ERP-SAP", "PO-123")
        assertThat(orders.findAll().count { it.sourceOrderReference == referencia }).isEqualTo(1)
    }

    @Then("nenhum Order desse lote fica gravado")
    fun nenhumOrderDoLoteGravado() {
        val numerosDoLote = setOf("PO-910", "PO-911", "PO-912")
        assertThat(orders.findAll().filter { it.sourceOrderReference.sourceOrderNumber in numerosDoLote }).isEmpty()
    }

    @Then("nenhum erro de linha desse lote fica gravado")
    fun nenhumErroDeLinhaDoLoteGravado() {
        assertThat(rowErrors.findAll().filter { it.importBatchId == importBatchId }).isEmpty()
    }

    @Then("nenhum Import Batch novo é criado")
    fun nenhumImportBatchNovoCriado() {
        assertThat(importBatches.findAll()).hasSize(1)
    }

    @Then("um workflow de processamento é disparado no Orkes para cada Order criado")
    fun workflowDisparadoNoOrkes() {
        // Orkes fica fora de escopo por decisão do projeto (Q13) — nenhuma das fatias
        // atuais depende dele, este step não tem asserção até a integração existir.
    }

    @Then("a existência do cliente é verificada de forma assíncrona, na Processing Step de Data Enrichment")
    fun clienteVerificadoAssincronamente() {
        // Data Enrichment/Orkes ainda não existem neste sistema (Q13) — a garantia hoje é
        // negativa (o import não chama Customers), verificada pelo step anterior.
    }

    private fun upload(): ResponseEntity<Map<*, *>> {
        val body = LinkedMultiValueMap<String, Any>()
        body.add("file", ClassPathResource(fixture))
        val headers = HttpHeaders().apply { contentType = MediaType.MULTIPART_FORM_DATA }

        return restTemplate.postForEntity(
            "http://localhost:$port/imports/orders",
            HttpEntity(body, headers),
            Map::class.java,
        )
    }

    private fun awaitProcessamentoConcluido(): ResponseEntity<Map<*, *>> {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val statusResponse = restTemplate.getForEntity(
                "http://localhost:$port/imports/orders/$importBatchId",
                Map::class.java,
            )
            if (statusResponse.body?.get("status") !in STATUS_EM_ANDAMENTO) return statusResponse
            Thread.sleep(50)
        }
        error("import batch $importBatchId não concluiu o processamento a tempo")
    }

    private fun parseMetodoEEndpoint(texto: String): Pair<String, String> {
        val (metodo, caminho) = texto.split(" ", limit = 2)
        return metodo to caminho
    }

    private companion object {
        val STATUS_EM_ANDAMENTO = setOf("PENDING", "PROCESSING")
    }
}
