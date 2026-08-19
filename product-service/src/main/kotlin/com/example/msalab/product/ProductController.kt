package com.example.msalab.product

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

data class Product(val id: Long, val name: String, val price: Long, val servedBy: String)

@RestController
class ProductController(
    @Value("\${HOSTNAME:local}") private val hostname: String,
) {
    private val products = mapOf(
        1L to ("k3s 학습용 키보드" to 39000L),
        2L to ("MSA 입문 마우스" to 15000L),
        3L to ("쿠버네티스 굿즈 텀블러" to 12000L),
    )

    @GetMapping("/products/{id}")
    fun getProduct(@PathVariable id: Long): Product {
        val (name, price) = products[id] ?: ("알 수 없는 상품" to 0L)
        return Product(id, name, price, hostname)
    }

    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf("status" to "UP", "servedBy" to hostname)
}
