package com.bonespirito.order.infra.web

import com.bonespirito.order.domain.ImportBatchStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.util.LinkedMultiValueMap
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Reenvio de arquivo cujo lote terminou `FAILED` (ADR order/0004, revisão 2026-10-04), contra o
 * contexto Spring real + H2 com a V5 aplicada pelo Flyway. O estado do banco é lido por JDBC.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReuploadAfterFailedTest {

    @Autowired private lateinit var restTemplate: TestRestTemplate

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val numeroDoPedido = "PO-REENVIO-${UUID.randomUUID()}"

    @Test
    fun `arquivo cujo lote terminou FAILED e reenviado vira lote novo com 202 e o FAILED fica intacto`() {
        // source_system_code com 60 caracteres passa na validação de linha e estoura o VARCHAR(50)
        // de orders (V2): o processamento real leva o lote a FAILED pelo markFailed.
        val conteudo = csv(sourceSystemCode = "ERP-SISTEMA-DE-ORIGEM-COM-CODIGO-MAIOR-QUE-A-COLUNA-ACEITA-X")
        val primeiro = upload(conteudo)
        assertThat(primeiro.statusCode).isEqualTo(HttpStatus.ACCEPTED)
        val loteFalho = idDe(primeiro)
        aguardaStatusTerminal(loteFalho)
        assertThat(statusDe(loteFalho)).isEqualTo("FAILED")
        val completedAtDoFalho = completedAtDe(loteFalho)

        val reenvio = upload(conteudo)

        assertThat(reenvio.statusCode).isEqualTo(HttpStatus.ACCEPTED)
        val loteNovo = idDe(reenvio)
        assertThat(loteNovo).isNotEqualTo(loteFalho)
        assertThat(statusDe(loteFalho)).isEqualTo("FAILED")
        assertThat(completedAtDe(loteFalho)).isEqualTo(completedAtDoFalho)
        assertThat(lotesDoArquivo()).isEqualTo(2)

        // O mesmo conteúdo falha de novo: dois FAILED do mesmo arquivo convivem (NULL não colide no UNIQUE).
        aguardaStatusTerminal(loteNovo)
        assertThat(statusDe(loteNovo)).isEqualTo("FAILED")
        assertThat(statusDe(loteFalho)).isEqualTo("FAILED")
        assertThat(completedAtDe(loteFalho)).isEqualTo(completedAtDoFalho)
    }

    @ParameterizedTest
    @EnumSource(
        value = ImportBatchStatus::class,
        names = ["PENDING", "PROCESSING", "COMPLETED", "COMPLETED_WITH_ERRORS"],
    )
    fun `lote que bloqueia continua dando 409 com o importBatchId dele, mesmo com um FAILED mais antigo`(
        status: ImportBatchStatus,
    ) {
        val conteudo = csv()
        insereLote(conteudo, ImportBatchStatus.FAILED)
        val loteQueBloqueia = insereLote(conteudo, status)

        val resposta = upload(conteudo)

        assertThat(resposta.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(idDe(resposta)).isEqualTo(loteQueBloqueia)
        assertThat(resposta.body?.get("status")).isEqualTo(status.name)
        assertThat(lotesDoArquivo()).isEqualTo(2)
    }

    @AfterEach
    fun limpa() {
        aguardaProcessamentoEmBackground()
        jdbcTemplate.update(
            "DELETE FROM import_batch_row_errors WHERE import_batch_id IN " +
                "(SELECT id FROM import_batches WHERE file_name = ?)",
            nomeDoArquivo(),
        )
        jdbcTemplate.update(
            "DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE source_order_number = ?)",
            numeroDoPedido,
        )
        jdbcTemplate.update("DELETE FROM orders WHERE source_order_number = ?", numeroDoPedido)
        jdbcTemplate.update("DELETE FROM import_batches WHERE file_name = ?", nomeDoArquivo())
    }

    private fun csv(sourceSystemCode: String = "ERP-SAP"): ByteArray = """
        source_system_code,source_order_number,customer_id,order_created_at,sku,quantity
        $sourceSystemCode,$numeroDoPedido,11111111-1111-1111-1111-111111111111,2026-09-07T10:00:00Z,SKU-1,2
    """.trimIndent().toByteArray()

    /** Lote semeado direto no banco, respeitando o CHECK de V5: `FAILED` sem fingerprint ativo. */
    private fun insereLote(conteudo: ByteArray, status: ImportBatchStatus): UUID {
        val id = UUID.randomUUID()
        val fingerprint = fingerprintDe(conteudo)
        val terminado = status != ImportBatchStatus.PENDING && status != ImportBatchStatus.PROCESSING
        jdbcTemplate.update(
            """
            INSERT INTO import_batches (id, file_name, status, created_at, completed_at, file_fingerprint, active_fingerprint)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            nomeDoArquivo(),
            status.name,
            Timestamp.from(Instant.now()),
            if (terminado) Timestamp.from(Instant.now()) else null,
            fingerprint,
            if (status == ImportBatchStatus.FAILED) null else fingerprint,
        )
        return id
    }

    private fun fingerprintDe(conteudo: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(conteudo).joinToString("") { "%02x".format(it) }

    private fun upload(conteudo: ByteArray): ResponseEntity<Map<*, *>> {
        val arquivo = object : ByteArrayResource(conteudo) {
            override fun getFilename() = nomeDoArquivo()
        }
        val body = LinkedMultiValueMap<String, Any>().apply { add("file", arquivo) }
        val headers = HttpHeaders().apply { contentType = MediaType.MULTIPART_FORM_DATA }
        return restTemplate.postForEntity("/imports/orders", HttpEntity(body, headers), Map::class.java)
    }

    private fun idDe(resposta: ResponseEntity<Map<*, *>>): UUID =
        UUID.fromString(resposta.body?.get("importBatchId") as String)

    private fun nomeDoArquivo() = "$numeroDoPedido.csv"

    private fun statusDe(id: UUID): String? =
        jdbcTemplate.queryForObject("SELECT status FROM import_batches WHERE id = ?", String::class.java, id)

    private fun completedAtDe(id: UUID): Timestamp? =
        jdbcTemplate.queryForObject("SELECT completed_at FROM import_batches WHERE id = ?", Timestamp::class.java, id)

    private fun lotesDoArquivo(): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM import_batches WHERE file_name = ?",
            Int::class.java,
            nomeDoArquivo(),
        ) ?: 0

    private fun aguardaStatusTerminal(id: UUID) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && statusDe(id) in setOf("PENDING", "PROCESSING")) {
            Thread.sleep(50)
        }
    }

    /** Só lotes criados pelo upload ficam em background; os semeados não têm processamento. */
    private fun aguardaProcessamentoEmBackground() {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val emAndamento = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM import_batches
                WHERE file_name = ? AND status IN ('PENDING', 'PROCESSING') AND total_rows IS NOT NULL
                """.trimIndent(),
                Int::class.java,
                nomeDoArquivo(),
            ) ?: 0
            if (emAndamento == 0) return
            Thread.sleep(50)
        }
    }
}
