package com.bonespirito.stock

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class StockApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<StockApplication>(*args)
}
