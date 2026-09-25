package com.bonespirito.order.acceptance

import io.cucumber.java.Before
import org.springframework.jdbc.core.JdbcTemplate

class Hooks(private val jdbcTemplate: JdbcTemplate) {

    @Before
    fun limpaEstadoEntreCenarios() {
        // O cenário "processa em background" devolve 202 sem esperar o executor —
        // sem esta espera, a limpeza abaixo disputaria com o insert do cenário
        // anterior (order_items criado entre o DELETE de order_items e o de orders).
        aguardaProcessamentoEmBackground()
        jdbcTemplate.execute("DELETE FROM order_items")
        jdbcTemplate.execute("DELETE FROM orders")
        jdbcTemplate.execute("DELETE FROM import_batch_row_errors")
        jdbcTemplate.execute("DELETE FROM import_batches")
    }

    private fun aguardaProcessamentoEmBackground() {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val pendentes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM import_batches WHERE status = 'PENDING'",
                Int::class.java,
            ) ?: 0
            if (pendentes == 0) return
            Thread.sleep(50)
        }
    }
}
