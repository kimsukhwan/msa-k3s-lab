package com.example.msalab.gateway

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

// Spring Security 필터 체인(-100)보다 먼저 돈다 — "JWT가 유효한지"를 따지기도 전에 포트부터
// 막아야, 이 차단이 JWT 문제가 아니라 채널(포트) 문제라는 게 데모에서 분명해진다.
private const val BEFORE_SECURITY_ORDER = -200

/**
 * 평문 HTTP 포트(server.port)는 k8s 헬스체크 전용이다. 업무 경로(/api/orders, /api/products,
 * 로그인 등 업무 경로까지 이 포트로 받아버리면 mTLS 포트를 그냥 우회해 아무나 호출할 수 있게 되어,
 * mTLS 커넥터를 추가한 의미가 없어진다. 이 필터가 그 우회를 막는 지점이다.
 *
 * mtls.enabled=false(로컬 개발)면 포트가 하나뿐이므로 이 필터도 통과만 시킨다.
 */
@Component
@Order(BEFORE_SECURITY_ORDER)
class PlainPortRestrictionFilter(
    @Value("\${server.port}") private val plainPort: Int,
    @Value("\${mtls.enabled:false}") private val mtlsEnabled: Boolean,
) : OncePerRequestFilter() {

    private val openPaths = setOf("/api/health", "/actuator/health", "/actuator/prometheus")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val hitPlainPort = request.localPort == plainPort
        val isOpenPath = openPaths.any { request.requestURI.startsWith(it) }
        if (mtlsEnabled && hitPlainPort && !isOpenPath) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = "application/json;charset=UTF-8"
            response.writer.write("""{"error":"이 경로는 mTLS 포트로만 접근할 수 있습니다"}""")
            return
        }
        chain.doFilter(request, response)
    }
}
