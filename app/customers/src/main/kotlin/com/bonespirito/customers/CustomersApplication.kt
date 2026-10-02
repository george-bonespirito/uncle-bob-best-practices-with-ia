package com.bonespirito.customers

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CustomersApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<CustomersApplication>(*args)
}
