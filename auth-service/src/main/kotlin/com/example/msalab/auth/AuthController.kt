package com.example.msalab.auth

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class LoginRequest(val username: String, val password: String)
data class TokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long,
    val custKey: String,
)

@RestController
class AuthController(
    private val jwtIssuer: JwtIssuer,
    @Value("\${HOSTNAME:local}") private val hostname: String,
) {
    private val log = LoggerFactory.getLogger(AuthController::class.java)

    data class DemoUser(val password: String, val custKey: String)

    // 랩 데모 계정 — 실서비스라면 슈퍼앱 쪽 사용자 저장소가 하는 일이다.
    // guest 는 은행에 매핑이 없는 고객키(SA-99999)를 갖는다 — fail-closed 시연용.
    private val demoUsers = mapOf(
        "demo" to DemoUser("demo1234", "SA-10001"),
        "kim" to DemoUser("kim1234", "SA-10002"),
        "guest" to DemoUser("guest1234", "SA-99999"),
    )

    @PostMapping("/auth/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<Any> {
        val user = demoUsers[request.username]
        if (user == null || user.password != request.password) {
            log.info("로그인 실패 username={}", request.username)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "아이디 또는 비밀번호가 올바르지 않습니다"))
        }
        log.info("로그인 성공 — 은행용(aud={}) 토큰 발급 username={} custKey={}", JwtIssuer.BANK_AUDIENCE, request.username, user.custKey)
        return ResponseEntity.ok(
            TokenResponse(
                accessToken = jwtIssuer.issue(request.username, user.custKey),
                expiresInSeconds = jwtIssuer.tokenTtl.seconds,
                custKey = user.custKey,
            ),
        )
    }

    // 공격 시연용 — 같은 IdP 가 "다른 제휴사(partner-mall)용"으로 발급한 토큰.
    // 서명은 진짜지만 aud 가 다르므로, 은행 gateway 의 aud 검증이 있어야만 재사용이 막힌다.
    @PostMapping("/auth/demo-partner-token")
    fun demoPartnerToken(@RequestBody request: LoginRequest): ResponseEntity<Any> {
        val user = demoUsers[request.username]
        if (user == null || user.password != request.password) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "아이디 또는 비밀번호가 올바르지 않습니다"))
        }
        log.info("제휴사용(aud=partner-mall) 토큰 발급 — 재사용 공격 시연용 username={}", request.username)
        return ResponseEntity.ok(
            TokenResponse(
                accessToken = jwtIssuer.issue(request.username, user.custKey, audience = "partner-mall"),
                expiresInSeconds = jwtIssuer.tokenTtl.seconds,
                custKey = user.custKey,
            ),
        )
    }

    /** 검증측이 서명 공개키를 받아 가는 표준 경로. */
    @GetMapping("/.well-known/jwks.json")
    fun jwks(): Map<String, Any> = jwtIssuer.jwks()

    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf("status" to "UP", "servedBy" to hostname)
}
