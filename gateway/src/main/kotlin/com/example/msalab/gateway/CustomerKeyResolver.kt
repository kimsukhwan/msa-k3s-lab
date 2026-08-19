package com.example.msalab.gateway

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 슈퍼앱 고객키(custKey, JWT의 custKey 클레임) → 내부 고유키(custId) 변환의 유일한 지점.
 *
 * 이 아래로는 절대 custKey 가 흐르지 않는다 — 하위 서비스는 custId 만 안다.
 * 매핑이 없는 custKey 는 예외 없이 거부한다(fail-closed) — "고객 없음"과 "권한 없음"을
 * 구분해서 알려주면 고객키 열거(enumeration) 공격의 재료가 되므로 항상 동일하게 거부한다.
 */
@Component
class CustomerKeyResolver {
    private val log = LoggerFactory.getLogger(CustomerKeyResolver::class.java)

    // 랩 데모용 인메모리 매핑 — 실서비스라면 customer-service 조회(cust_unno)에 해당한다.
    // SA-99999(guest)는 의도적으로 매핑을 비워 fail-closed 를 시연한다.
    private val custKeyToCustId = mapOf(
        "SA-10001" to "CUST000001",
        "SA-10002" to "CUST000002",
    )

    class UnknownCustomerKeyException(custKey: String) : RuntimeException("매핑되지 않은 고객키: $custKey")

    /** custKey 를 내부 custId 로 변환한다. 매핑이 없으면 예외를 던진다(fail-closed). */
    fun resolve(custKey: String): String =
        custKeyToCustId[custKey] ?: run {
            log.warn("고객키 매핑 실패 — fail-closed 로 거부 custKey={}", custKey)
            throw UnknownCustomerKeyException(custKey)
        }
}
