package com.example.msalab.gateway

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.Instant

@RestController
@RequestMapping("/api")
class GatewayController(
    @Qualifier("orderServiceClient") private val orderServiceClient: RestClient,
    @Qualifier("productServiceClient") private val productServiceClient: RestClient,
    @Qualifier("authServiceClient") private val authServiceClient: RestClient,
    private val tokenRevocationService: TokenRevocationService,
    @Value("\${HOSTNAME:local}") private val hostname: String,
) {

    // 로그인은 인증 없이 통과하는 유일한 업무 경로 — auth-service 로 그대로 전달한다.
    // 실패(401)도 본문 그대로 되돌려줘 프론트가 사유를 표시할 수 있게 한다.
    @PostMapping("/auth/login")
    fun routeToLogin(@RequestBody body: Map<String, Any>): ResponseEntity<Any> = proxyToAuth("/auth/login", body)

    // 공격 시연 전용 — 같은 슈퍼앱 IdP가 "다른 제휴사(partner-mall)용"으로 서명한 토큰을 받아온다.
    // 이 토큰으로 /api/orders 등을 호출하면 gateway의 audience 검증에서 401이 나야 정상이다.
    @PostMapping("/auth/demo-partner-token")
    fun routeToPartnerToken(@RequestBody body: Map<String, Any>): ResponseEntity<Any> =
        proxyToAuth("/auth/demo-partner-token", body)

    // 로그아웃은 이 토큰 자신의 jti 를 폐기 목록에 올리는 것 — 그래서 유효한 토큰이 있어야만
    // 부를 수 있다(SecurityConfig 에서 permitAll 대상이 아니다). 클라이언트가 메모리에서
    // 토큰을 지우는 것과 달리, 이건 서버 쪽에서 그 토큰을 즉시 무효화한다.
    @PostMapping("/auth/logout")
    fun logout(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<Any> {
        val jti = jwt.id ?: return ResponseEntity.badRequest().body(mapOf("error" to "토큰에 jti 가 없습니다"))
        val remaining = Duration.between(Instant.now(), jwt.expiresAt ?: Instant.now())
        tokenRevocationService.revoke(jti, remaining)
        return ResponseEntity.ok(mapOf("status" to "revoked"))
    }

    private fun proxyToAuth(path: String, body: Map<String, Any>): ResponseEntity<Any> =
        try {
            ResponseEntity.ok(
                authServiceClient.post().uri(path).body(body).retrieve().body(Any::class.java)
                    ?: emptyMap<String, Any>(),
            )
        } catch (e: HttpClientErrorException) {
            ResponseEntity.status(e.statusCode).body(e.getResponseBodyAs(Any::class.java) ?: emptyMap<String, Any>())
        }

    // 실제 Envoy Gateway/HTTPRoute가 하는 일(Path 기준 라우팅)을 최소 형태로 흉내낸 것
    @GetMapping("/orders/{productId}")
    fun routeToOrder(@PathVariable productId: Long): Any =
        orderServiceClient.get().uri("/orders/{id}", productId).retrieve().body(Any::class.java) ?: emptyMap<String, Any>()

    @GetMapping("/products/{id}")
    fun routeToProduct(@PathVariable id: Long): Any =
        productServiceClient.get().uri("/products/{id}", id).retrieve().body(Any::class.java) ?: emptyMap<String, Any>()

    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf("status" to "UP", "servedBy" to hostname)
}
