package com.bonespirito.order.acceptance

import io.cucumber.java.Before
import org.springframework.jdbc.core.JdbcTemplate

class Hooks(private val jdbcTemplate: JdbcTemplate) {

    @Before
    fun limpaEstadoEntreCenarios() {
        jdbcTemplate.execute("DELETE FROM order_items")
        jdbcTemplate.execute("DELETE FROM orders")
        jdbcTemplate.execute("DELETE FROM import_batch_row_errors")
        jdbcTemplate.execute("DELETE FROM import_batches")
    }
}
