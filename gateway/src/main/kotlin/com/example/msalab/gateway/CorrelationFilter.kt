package com.example.msalab.gateway

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

// gateway는 실제 진입점 — React가 X-Action-Id를 안 보내는 경우를 대비해 여기서 새로 만든다.
@Component
class CorrelationFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(CorrelationFilter::class.java)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val actionId = request.getHeader("X-Action-Id") ?: UUID.randomUUID().toString()
        val requestId = UUID.randomUUID().toString()
        val channel = request.getHeader("X-Channel") ?: "web"

        MDC.put("actionId", actionId)
        MDC.put("requestId", requestId)
        MDC.put("channel", channel)
        response.setHeader("X-Action-Id", actionId)

        val start = System.currentTimeMillis()
        try {
            chain.doFilter(request, response)
        } finally {
            val durationMs = System.currentTimeMillis() - start
            log.info(
                "{} {} -> {} ({}ms)",
                request.method, request.requestURI, response.status, durationMs,
            )
            MDC.clear()
        }
    }
}
