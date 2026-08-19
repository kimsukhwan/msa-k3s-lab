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
data class TokenResponse(val accessToken: String, val tokenType: String = "Bearer", val expiresInSeconds: Long)

@RestController
class AuthController(
    private val jwtIssuer: JwtIssuer,
    @Value("\${HOSTNAME:local}") private val hostname: String,
) {
    private val log = LoggerFactory.getLogger(AuthController::class.java)

    // 랩 데모 계정 — 실서비스라면 사용자 저장소의 BCrypt 해시와 비교한다.
    private val demoUsers = mapOf("demo" to "demo1234", "kim" to "kim1234")

    @PostMapping("/auth/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<Any> {
        if (demoUsers[request.username] != request.password) {
            log.info("로그인 실패 username={}", request.username)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "아이디 또는 비밀번호가 올바르지 않습니다"))
        }
        log.info("로그인 성공 — 토큰 발급 username={}", request.username)
        return ResponseEntity.ok(
            TokenResponse(
                accessToken = jwtIssuer.issue(request.username),
                expiresInSeconds = jwtIssuer.tokenTtl.seconds,
            ),
        )
    }

    /** 검증측이 서명 공개키를 받아 가는 표준 경로. */
    @GetMapping("/.well-known/jwks.json")
    fun jwks(): Map<String, Any> = jwtIssuer.jwks()

    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf("status" to "UP", "servedBy" to hostname)
}
