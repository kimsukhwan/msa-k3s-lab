package com.example.msalab.order

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

data class OrderPlacedEvent(
    val orderId: Long,
    val productId: Long,
    val productName: String,
    val price: Long,
    val orderedBy: String,
)

@Configuration
class OrderTopicConfig {
    // 단일 브로커 랩 — 파티션 1, 복제계수 1
    @Bean
    fun orderEventsTopic(): NewTopic = NewTopic("order-events", 1, 1.toShort())
}

// 주문 확정 사실을 Kafka로 알린다. 발행은 비동기이고, 실패해도 주문 응답을 실패시키지 않는다(로그만 남긴다).
@Component
class OrderEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(OrderEventPublisher::class.java)

    fun publish(event: OrderPlacedEvent) {
        // 콜백은 producer 스레드에서 돌아 MDC가 비어 있으므로, actionId를 지금 캡처해 로그 본문에 싣는다
        // — Loki에서 actionId 전문검색으로 이 로그까지 묶이게 하기 위함.
        val actionId = MDC.get("actionId")
        val record = ProducerRecord("order-events", event.productId.toString(), objectMapper.writeValueAsString(event))
        actionId?.let { record.headers().add("X-Action-Id", it.toByteArray()) }

        kafkaTemplate.send(record).whenComplete { result, ex ->
            if (ex != null) {
                log.warn("order-events 발행 실패 actionId={} orderId={} — {}", actionId, event.orderId, ex.message)
            } else {
                log.info(
                    "order-events 발행 actionId={} orderId={} productId={} offset={}",
                    actionId, event.orderId, event.productId, result.recordMetadata.offset(),
                )
            }
        }
    }
}
