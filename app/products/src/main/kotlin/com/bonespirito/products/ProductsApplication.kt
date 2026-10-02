package com.bonespirito.products

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ProductsApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<ProductsApplication>(*args)
}
