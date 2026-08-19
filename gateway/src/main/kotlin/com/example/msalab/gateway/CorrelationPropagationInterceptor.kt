package com.example.msalab.gateway

import org.slf4j.MDC
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

class CorrelationPropagationInterceptor : ClientHttpRequestInterceptor {
    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
        MDC.get("actionId")?.let { request.headers.set("X-Action-Id", it) }
        MDC.get("channel")?.let { request.headers.set("X-Channel", it) }
        // custId 는 CustomerContextFilter 가 검증된 JWT 의 custKey 를 변환해 MDC 에 넣어둔 값이다 —
        // 하위 서비스는 이 내부 고유키만 보고, 원본 custKey(슈퍼앱 고객키)는 여기서 끝난다.
        MDC.get("custId")?.let { request.headers.set("X-Cust-Id", it) }
        return execution.execute(request, body)
    }
}
