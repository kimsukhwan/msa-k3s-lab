package com.example.msalab.product

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

data class Product(val id: Long, val name: String, val price: Long, val servedBy: String, val source: String)

@RestController
class ProductController(
    private val catalog: ProductCatalog,
    @Value("\${HOSTNAME:local}") private val hostname: String,
) {

    @GetMapping("/products/{id}")
    fun getProduct(@PathVariable id: Long): Product {
        val lookup = catalog.find(id)
        // source 로 이번 응답이 캐시에서 왔는지(redis-cache) 원장에서 왔는지(origin) 화면에서 바로 보인다
        return Product(
            id = lookup.product.id,
            name = lookup.product.name,
            price = lookup.product.price,
            servedBy = hostname,
            source = if (lookup.fromCache) "redis-cache" else "origin",
        )
    }

    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf("status" to "UP", "servedBy" to hostname)
}
