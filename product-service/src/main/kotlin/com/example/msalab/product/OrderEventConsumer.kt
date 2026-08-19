package com.example.msalab.product

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

// order-service 가 발행한 주문 이벤트를 받아 재고 차감을 시뮬레이션한다.
// HTTP hop 이 아닌 비동기(Kafka) hop 에서도 actionId 로 로그가 묶이는 것을 보여주는 것이 목적 —
// 이벤트 헤더의 X-Action-Id 를 MDC 에 복원해 이 로그도 같은 actionId 로 검색된다.
@Component
class OrderEventConsumer {
    private val log = LoggerFactory.getLogger(OrderEventConsumer::class.java)

    @KafkaListener(topics = ["order-events"], groupId = "product-service")
    fun onOrderPlaced(record: ConsumerRecord<String, String>) {
        val actionId = record.headers().lastHeader("X-Action-Id")?.value()?.decodeToString()
        actionId?.let { MDC.put("actionId", it) }
        try {
            log.info("주문 이벤트 수신 — 재고 차감 시뮬레이션 productId(key)={} payload={}", record.key(), record.value())
        } finally {
            MDC.clear()
        }
    }
}
