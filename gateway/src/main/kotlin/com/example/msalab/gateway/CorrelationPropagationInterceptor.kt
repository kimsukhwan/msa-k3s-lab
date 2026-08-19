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
        return execution.execute(request, body)
    }
}
