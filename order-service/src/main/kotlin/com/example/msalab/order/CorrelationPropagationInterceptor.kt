package com.example.msalab.order

import org.slf4j.MDC
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

// 하위 서비스 호출에 actionId를 그대로 실어 보낸다 — requestId는 매 hop마다 새로 만드므로 전파하지 않는다.
class CorrelationPropagationInterceptor : ClientHttpRequestInterceptor {
    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
        MDC.get("actionId")?.let { request.headers.set("X-Action-Id", it) }
        MDC.get("channel")?.let { request.headers.set("X-Channel", it) }
        // gateway가 부여한 내부 고유키 — CorrelationFilter가 요청 헤더에서 받아 MDC에 넣어둔 값을
        // 다음 hop(product-service)에도 그대로 전달한다.
        MDC.get("custId")?.let { request.headers.set("X-Cust-Id", it) }
        return execution.execute(request, body)
    }
}
