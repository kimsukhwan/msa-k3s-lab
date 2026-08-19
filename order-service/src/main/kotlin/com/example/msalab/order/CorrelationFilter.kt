package com.example.msalab.order

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

// 버튼 클릭 하나(actionId)가 여러 서비스에 걸쳐 남기는 로그를 나중에 한 번에 묶어 볼 수 있게 한다.
@Component
class CorrelationFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(CorrelationFilter::class.java)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val actionId = request.getHeader("X-Action-Id") ?: UUID.randomUUID().toString()
        val requestId = UUID.randomUUID().toString()
        val channel = request.getHeader("X-Channel") ?: "web"
        // gateway가 JWT의 custKey를 검증·변환해 내려보낸 내부 고유키 — 원본 고객키는 여기 없다.
        val custId = request.getHeader("X-Cust-Id")

        MDC.put("actionId", actionId)
        MDC.put("requestId", requestId)
        MDC.put("channel", channel)
        custId?.let { MDC.put("custId", it) }
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
