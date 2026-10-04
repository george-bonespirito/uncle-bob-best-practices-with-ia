package com.bonespirito.order.infra.web

import com.bonespirito.order.application.port.ImportBatchRepository
import com.bonespirito.order.domain.ImportBatch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dois uploads do mesmo arquivo ao mesmo tempo, contra o contexto Spring real + H2.
 *
 * A corrida é forçada, não sorteada: a barreira segura as duas requisições logo depois da
 * consulta do lote que bloqueia, então as duas veem "arquivo livre" e as duas tentam o INSERT —
 * uma delas bate no `uq_import_batches_active_fingerprint` (V5; antes, o UNIQUE de V4).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ConcurrentDuplicateUploadTest.BarreiraConfig::class)
class ConcurrentDuplicateUploadTest {

    @Autowired private lateinit var restTemplate: TestRestTemplate

    @Autowired private lateinit var importBatches: BarreiraNaConsultaDeFingerprint

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val numeroDoPedido = "PO-CORRIDA-${UUID.randomUUID()}"

    @Test
    fun `dois uploads simultaneos do mesmo arquivo dao um 202 e um 409 com o lote vencedor, nunca 500`() {
        val conteudo = """
            source_system_code,source_order_number,customer_id,order_created_at,sku,quantity
            ERP-SAP,$numeroDoPedido,11111111-1111-1111-1111-111111111111,2026-09-07T10:00:00Z,SKU-1,2
        """.trimIndent().toByteArray()
        importBatches.arma(participantes = 2)

        val largada = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val respostas = List(2) {
            pool.submit<ResponseEntity<Map<*, *>>> {
                largada.await()
                upload(conteudo)
            }
        }
        largada.countDown()
        val resultado = respostas.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertThat(resultado.map { it.statusCode }).containsExactlyInAnyOrder(HttpStatus.ACCEPTED, HttpStatus.CONFLICT)
        val aceito = resultado.single { it.statusCode == HttpStatus.ACCEPTED }
        val recusado = resultado.single { it.statusCode == HttpStatus.CONFLICT }
        assertThat(recusado.body?.get("importBatchId")).isEqualTo(aceito.body?.get("importBatchId"))
        assertThat(lotesDoArquivo()).isEqualTo(1)
    }

    @Test
    fun `dois reenvios simultaneos depois de um lote FAILED dao um 202 e um 409 com um unico lote novo`() {
        val conteudo = """
            source_system_code,source_order_number,customer_id,order_created_at,sku,quantity
            ERP-SAP,$numeroDoPedido,11111111-1111-1111-1111-111111111111,2026-09-07T10:00:00Z,SKU-1,2
        """.trimIndent().toByteArray()
        val loteFalho = UUID.randomUUID()
        val completedAtDoFalho = Timestamp.from(Instant.parse("2026-10-01T12:00:00Z"))
        jdbcTemplate.update(
            """
            INSERT INTO import_batches (id, file_name, status, created_at, completed_at, file_fingerprint, active_fingerprint)
            VALUES (?, ?, 'FAILED', ?, ?, ?, NULL)
            """.trimIndent(),
            loteFalho,
            nomeDoArquivo(),
            completedAtDoFalho,
            completedAtDoFalho,
            MessageDigest.getInstance("SHA-256").digest(conteudo).joinToString("") { "%02x".format(it) },
        )
        importBatches.arma(participantes = 2)

        val largada = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val respostas = List(2) {
            pool.submit<ResponseEntity<Map<*, *>>> {
                largada.await()
                upload(conteudo)
            }
        }
        largada.countDown()
        val resultado = respostas.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertThat(resultado.map { it.statusCode }).containsExactlyInAnyOrder(HttpStatus.ACCEPTED, HttpStatus.CONFLICT)
        val aceito = resultado.single { it.statusCode == HttpStatus.ACCEPTED }
        val recusado = resultado.single { it.statusCode == HttpStatus.CONFLICT }
        assertThat(recusado.body?.get("importBatchId")).isEqualTo(aceito.body?.get("importBatchId"))
        assertThat(aceito.body?.get("importBatchId")).isNotEqualTo(loteFalho.toString())
        assertThat(lotesDoArquivo()).isEqualTo(2)
        val falhoDepois = jdbcTemplate.queryForMap(
            "SELECT status, completed_at FROM import_batches WHERE id = ?",
            loteFalho,
        )
        assertThat(falhoDepois["STATUS"]).isEqualTo("FAILED")
        assertThat(falhoDepois["COMPLETED_AT"]).isEqualTo(completedAtDoFalho)
    }

    @AfterEach
    fun limpa() {
        importBatches.desarma()
        aguardaProcessamentoEmBackground()
        jdbcTemplate.update(
            "DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE source_order_number = ?)",
            numeroDoPedido,
        )
        jdbcTemplate.update("DELETE FROM orders WHERE source_order_number = ?", numeroDoPedido)
        jdbcTemplate.update("DELETE FROM import_batches WHERE file_name = ?", nomeDoArquivo())
    }

    private fun upload(conteudo: ByteArray): ResponseEntity<Map<*, *>> {
        val arquivo = object : ByteArrayResource(conteudo) {
            override fun getFilename() = nomeDoArquivo()
        }
        val body = LinkedMultiValueMap<String, Any>().apply { add("file", arquivo) }
        val headers = HttpHeaders().apply { contentType = MediaType.MULTIPART_FORM_DATA }
        return restTemplate.postForEntity("/imports/orders", HttpEntity(body, headers), Map::class.java)
    }

    private fun nomeDoArquivo() = "$numeroDoPedido.csv"

    private fun lotesDoArquivo(): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM import_batches WHERE file_name = ?",
            Int::class.java,
            nomeDoArquivo(),
        ) ?: 0

    private fun aguardaProcessamentoEmBackground() {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val emAndamento = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM import_batches WHERE file_name = ? AND status IN ('PENDING', 'PROCESSING')",
                Int::class.java,
                nomeDoArquivo(),
            ) ?: 0
            if (emAndamento == 0) return
            Thread.sleep(50)
        }
    }

    /** O repositório real, com uma barreira entre a consulta por fingerprint e o que vem depois. */
    class BarreiraNaConsultaDeFingerprint(
        private val real: ImportBatchRepository,
    ) : ImportBatchRepository by real {
        @Volatile private var barreira: CyclicBarrier? = null
        private val chegadas = AtomicInteger()

        fun arma(participantes: Int) {
            chegadas.set(participantes)
            barreira = CyclicBarrier(participantes)
        }

        fun desarma() {
            barreira = null
        }

        override fun findBlockingByFingerprint(fingerprint: String): ImportBatch? {
            val encontrado = real.findBlockingByFingerprint(fingerprint)
            val atual = barreira
            if (atual != null && chegadas.getAndDecrement() > 0) atual.await(5, TimeUnit.SECONDS)
            return encontrado
        }
    }

    @TestConfiguration
    class BarreiraConfig {
        @Bean
        @Primary
        fun importBatchRepositoryComBarreira(
            @Qualifier("jpaImportBatchRepository") real: ImportBatchRepository,
        ) = BarreiraNaConsultaDeFingerprint(real)
    }
}
