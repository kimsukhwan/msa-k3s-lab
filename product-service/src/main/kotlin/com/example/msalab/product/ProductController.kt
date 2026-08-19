package com.example.msalab.product

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

data class Product(
    val id: Long,
    val name: String,
    val price: Long,
    val servedBy: String,
    val source: String,
    // gateway가 JWT custKey를 검증해 내려보낸 내부 고유키. 이 서비스는 custKey(원본 고객키)를 모른다.
    val custId: String?,
)

@RestController
class ProductController(
    private val catalog: ProductCatalog,
    @Value("\${HOSTNAME:local}") private val hostname: String,
) {

    @GetMapping("/products/{id}")
    fun getProduct(@PathVariable id: Long, request: HttpServletRequest): Product {
        val lookup = catalog.find(id)
        // source 로 이번 응답이 캐시에서 왔는지(redis-cache) 원장에서 왔는지(origin) 화면에서 바로 보인다
        return Product(
            id = lookup.product.id,
            name = lookup.product.name,
            price = lookup.product.price,
            servedBy = hostname,
            source = if (lookup.fromCache) "redis-cache" else "origin",
            custId = request.getHeader("X-Cust-Id"),
        )
    }

    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf("status" to "UP", "servedBy" to hostname)
}
