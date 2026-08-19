package com.example.msalab.order

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient

data class ProductView(val id: Long, val name: String, val price: Long, val servedBy: String)
data class OrderView(val orderId: Long, val product: ProductView, val orderedBy: String)

@RestController
@RequestMapping("/orders")
class OrderController(
    private val productServiceClient: RestClient,
    private val orderEventPublisher: OrderEventPublisher,
    @Value("\${HOSTNAME:local}") private val hostname: String,
) {

    @GetMapping("/{productId}")
    fun placeOrder(@PathVariable productId: Long): OrderView {
        // product-service를 k8s Service DNS 이름으로 호출한다 — 직접 Pod IP를 몰라도 된다.
        val product = productServiceClient.get()
            .uri("/products/{id}", productId)
            .retrieve()
            .body(ProductView::class.java)
            ?: error("product-service 응답 없음")

        val order = OrderView(orderId = System.nanoTime() % 100000, product = product, orderedBy = hostname)

        // 주문 확정 이벤트 — product-service 가 구독해 재고 차감을 시뮬레이션한다.
        orderEventPublisher.publish(
            OrderPlacedEvent(
                orderId = order.orderId,
                productId = product.id,
                productName = product.name,
                price = product.price,
                orderedBy = hostname,
            ),
        )
        return order
    }

    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf("status" to "UP", "servedBy" to hostname)
}
