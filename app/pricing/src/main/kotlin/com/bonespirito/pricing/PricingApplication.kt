package com.bonespirito.pricing

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PricingApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<PricingApplication>(*args)
}
