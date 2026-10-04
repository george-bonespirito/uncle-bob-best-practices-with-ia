package com.bonespirito.order.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ImportBatchTest {

    private fun novoLote() = ImportBatch(uploadedFile = UploadedFile("orders.csv", "fp-abc"))

    @Test
    fun `lote novo bloqueia o reenvio do proprio arquivo`() {
        assertThat(novoLote().activeFingerprint).isEqualTo("fp-abc")
    }

    @Test
    fun `lote que processa ou termina sem falha continua bloqueando o reenvio`() {
        val processando = novoLote().apply { start() }
        val concluido = novoLote().apply { start(); finish(hasErrors = false) }
        val comErros = novoLote().apply { start(); finish(hasErrors = true) }

        assertThat(listOf(processando, concluido, comErros).map { it.activeFingerprint })
            .containsOnly("fp-abc")
    }

    @Test
    fun `lote FAILED libera o arquivo para reenvio e guarda o fingerprint do arquivo como historico`() {
        val lote = novoLote().apply { start() }

        lote.fail()

        assertThat(lote.status).isEqualTo(ImportBatchStatus.FAILED)
        assertThat(lote.completedAt).isNotNull()
        assertThat(lote.activeFingerprint).isNull()
        assertThat(lote.uploadedFile.fingerprint).isEqualTo("fp-abc")
    }
}
