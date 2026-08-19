package com.example.msalab.gateway

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Spring Security가 JWT 서명·aud·만료를 검증하고 통과시킨 "이후"에 실행된다(Security 필터 뒤).
 * 검증된 토큰의 custKey 클레임을 꺼내 내부 custId로 변환하고, 이후 요청 처리(하위 서비스 호출
 * 포함) 동안 쓸 수 있도록 MDC에 넣는다.
 *
 * 매핑이 없으면 여기서 즉시 403으로 끊는다 — 하위 서비스까지 보내지 않는다(fail-closed).
 */
// Security 필터 체인은 매우 앞쪽 순위(-100)에 등록되므로, 명시적 순서를 낮게 잡아
// "그 뒤"임을 분명히 한다 — 이 시점의 SecurityContext 에는 이미 검증된 JWT 가 들어 있다.
private const val AFTER_SECURITY_ORDER = 200

@Component
@Order(AFTER_SECURITY_ORDER)
class CustomerContextFilter(private val customerKeyResolver: CustomerKeyResolver) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val auth = SecurityContextHolder.getContext().authentication
        if (auth is JwtAuthenticationToken) {
            val custKey = auth.token.getClaim<String>("custKey")
            try {
                val custId = customerKeyResolver.resolve(custKey)
                MDC.put("custId", custId)
                response.setHeader("X-Cust-Id", custId) // 화면에서 바로 확인할 수 있도록 노출(랩 전용)
            } catch (e: CustomerKeyResolver.UnknownCustomerKeyException) {
                response.status = HttpServletResponse.SC_FORBIDDEN
                response.contentType = "application/json;charset=UTF-8"
                response.writer.write("""{"error":"고객 확인이 되지 않습니다"}""")
                return
            }
        }
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove("custId")
        }
    }
}
