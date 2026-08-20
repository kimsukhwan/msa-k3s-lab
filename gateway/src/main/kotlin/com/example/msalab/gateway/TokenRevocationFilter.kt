package com.example.msalab.gateway

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

// Security 필터 체인(-100) 이후, CustomerContextFilter(200)보다 먼저 돈다 — 폐기된 토큰이면
// custKey 변환까지 갈 필요 없이 여기서 끊는다.
private const val AFTER_SECURITY_BEFORE_CUSTOMER_CONTEXT_ORDER = 150

/** 서명·aud·만료 검증을 통과한 토큰이라도, jti가 폐기 목록에 있으면 401로 거부한다. */
@Component
@Order(AFTER_SECURITY_BEFORE_CUSTOMER_CONTEXT_ORDER)
class TokenRevocationFilter(private val revocationService: TokenRevocationService) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val auth = SecurityContextHolder.getContext().authentication
        if (auth is JwtAuthenticationToken) {
            val jti = auth.token.id
            if (jti != null && revocationService.isRevoked(jti)) {
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                response.contentType = "application/json;charset=UTF-8"
                response.writer.write("""{"error":"로그아웃되었거나 폐기된 토큰입니다"}""")
                return
            }
        }
        chain.doFilter(request, response)
    }
}
