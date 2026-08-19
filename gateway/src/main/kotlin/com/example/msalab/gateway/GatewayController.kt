package com.example.msalab.gateway

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient

@RestController
@RequestMapping("/api")
class GatewayController(
    @Qualifier("orderServiceClient") private val orderServiceClient: RestClient,
    @Qualifier("productServiceClient") private val productServiceClient: RestClient,
    @Value("\${HOSTNAME:local}") private val hostname: String,
) {

    // 실제 Envoy Gateway/HTTPRoute가 하는 일(Path 기준 라우팅)을 최소 형태로 흉내낸 것
    @GetMapping("/orders/{productId}")
    fun routeToOrder(@PathVariable productId: Long): Any =
        orderServiceClient.get().uri("/orders/{id}", productId).retrieve().body(Any::class.java) ?: emptyMap<String, Any>()

    @GetMapping("/products/{id}")
    fun routeToProduct(@PathVariable id: Long): Any =
        productServiceClient.get().uri("/products/{id}", id).retrieve().body(Any::class.java) ?: emptyMap<String, Any>()

    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf("status" to "UP", "servedBy" to hostname)
}
