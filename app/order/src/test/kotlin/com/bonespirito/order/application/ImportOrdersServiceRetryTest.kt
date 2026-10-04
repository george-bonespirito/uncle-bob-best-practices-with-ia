package com.bonespirito.order.application

import com.bonespirito.order.application.port.ImportBatchRepository
import com.bonespirito.order.domain.ImportBatch
import com.bonespirito.order.domain.ImportBatchStatus
import com.bonespirito.order.domain.UploadedFile
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.util.concurrent.ExecutorService

/**
 * Retry único do INSERT do lote (ADR order/0004, revisão 2026-10-04; architect-000154): só quando a
 * releitura depois da violação não acha lote que bloqueia; exatamente uma nova tentativa, sem laço.
 */
class ImportOrdersServiceRetryTest {

    private val executor = mockk<ExecutorService>(relaxed = true)

    private fun serviceCom(importBatches: ImportBatchRepository) = ImportOrdersService(
        importBatches = importBatches,
        orders = mockk(),
        rowErrors = mockk(),
        parser = mockk(),
        executor = executor,
        transaction = mockk(),
    )

    @Test
    fun `releitura sem lote que bloqueia tenta o INSERT uma vez mais e aceita o upload`() {
        val violacao = DataIntegrityViolationException("uq_import_batches_active_fingerprint")
        val repositorio = ImportBatchRepositoryFake(
            respostasDoInsert = listOf({ throw violacao }, { it }),
        )

        val result = serviceCom(repositorio).startImport("orders.csv", "header\nrow1".byteInputStream())

        assertThat(repositorio.tentativasDeInsert).hasSize(2)
        assertThat(repositorio.consultasDoLoteQueBloqueia).isEqualTo(2)
        assertThat(result).isEqualTo(
            ImportUploadResult.Accepted(repositorio.tentativasDeInsert.last().id, ImportBatchStatus.PENDING),
        )
        verify(exactly = 1) { executor.submit(any<Runnable>()) }
    }

    @Test
    fun `segunda tentativa que tambem viola sobe a violacao, sem terceira tentativa`() {
        val primeira = DataIntegrityViolationException("primeira")
        val segunda = DataIntegrityViolationException("segunda")
        val repositorio = ImportBatchRepositoryFake(
            respostasDoInsert = listOf({ throw primeira }, { throw segunda }),
        )

        val thrown = runCatching {
            serviceCom(repositorio).startImport("orders.csv", "header\nrow1".byteInputStream())
        }

        assertThat(thrown.exceptionOrNull()).isSameAs(segunda)
        assertThat(repositorio.tentativasDeInsert).hasSize(2)
        verify(exactly = 0) { executor.submit(any<Runnable>()) }
    }

    @Test
    fun `releitura que acha o lote vencedor devolve DuplicateFile, sem nova tentativa`() {
        val violacaoDoVencedor = DataIntegrityViolationException("uq_import_batches_active_fingerprint")
        val vencedor = ImportBatch(
            uploadedFile = UploadedFile("orders.csv", "fp-vencedor"),
        )
        val repositorio = ImportBatchRepositoryFake(
            respostasDoInsert = listOf({ throw violacaoDoVencedor }),
            loteQueBloqueiaNaReleitura = vencedor,
        )

        val result = serviceCom(repositorio).startImport("orders.csv", "header\nrow1".byteInputStream())

        assertThat(result).isEqualTo(ImportUploadResult.DuplicateFile(vencedor.id, vencedor.status))
        assertThat(repositorio.tentativasDeInsert).hasSize(1)
        verify(exactly = 0) { executor.submit(any<Runnable>()) }
    }

    /**
     * Fake do port: cada INSERT responde com a próxima resposta da lista (uma terceira tentativa
     * estoura o índice). A primeira consulta do lote que bloqueia devolve `null` (nenhum lote
     * bloqueia o upload); a releitura devolve [loteQueBloqueiaNaReleitura]. O resto do port não é
     * usado por `startImport` e fica num mock estrito.
     */
    private class ImportBatchRepositoryFake(
        private val respostasDoInsert: List<(ImportBatch) -> ImportBatch>,
        private val loteQueBloqueiaNaReleitura: ImportBatch? = null,
    ) : ImportBatchRepository by mockk() {
        val tentativasDeInsert = mutableListOf<ImportBatch>()
        var consultasDoLoteQueBloqueia = 0

        override fun save(importBatch: ImportBatch): ImportBatch {
            val resposta = respostasDoInsert[tentativasDeInsert.size]
            tentativasDeInsert += importBatch
            return resposta(importBatch)
        }

        override fun findBlockingByFingerprint(fingerprint: String): ImportBatch? =
            if (consultasDoLoteQueBloqueia++ == 0) null else loteQueBloqueiaNaReleitura
    }
}
