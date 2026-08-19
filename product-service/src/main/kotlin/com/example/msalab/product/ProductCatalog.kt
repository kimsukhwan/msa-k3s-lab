package com.example.msalab.product

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

data class ProductInfo(val id: Long, val name: String, val price: Long)

// 상품 원장 조회 + Redis 캐시(TTL 60초).
// Redis가 죽어 있어도 조회는 원장으로 계속 동작한다 — 캐시는 어디까지나 가속 장치.
@Service
class ProductCatalog(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(ProductCatalog::class.java)

    private val products = mapOf(
        1L to ("k3s 학습용 키보드" to 39000L),
        2L to ("MSA 입문 마우스" to 15000L),
        3L to ("쿠버네티스 굿즈 텀블러" to 12000L),
    )

    /** 조회 결과 + 캐시 적중 여부. */
    data class Lookup(val product: ProductInfo, val fromCache: Boolean)

    fun find(id: Long): Lookup {
        val key = "product:$id"

        runCatching { redis.opsForValue().get(key) }
            .onFailure { log.warn("캐시 조회 실패 — 원장으로 진행 productId={} ({})", id, it.message) }
            .getOrNull()
            ?.let {
                log.info("상품 캐시 적중 productId={}", id)
                return Lookup(objectMapper.readValue(it, ProductInfo::class.java), fromCache = true)
            }

        log.info("상품 캐시 미스 — 원장 조회 productId={}", id)
        // 원장(DB) 조회를 흉내내는 인위적 지연 — 캐시 적중과의 응답시간 차이를 화면에서 체감하기 위한 것
        Thread.sleep(200)
        val (name, price) = products[id] ?: ("알 수 없는 상품" to 0L)
        val product = ProductInfo(id, name, price)

        runCatching { redis.opsForValue().set(key, objectMapper.writeValueAsString(product), Duration.ofSeconds(60)) }
            .onFailure { log.warn("캐시 저장 실패 productId={} ({})", id, it.message) }
        return Lookup(product, fromCache = false)
    }
}
