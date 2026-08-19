package com.example.msalab.gateway

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

// 인증 경계는 gateway 하나다 — 여기서 JWT(JWKS 서명 검증)를 통과한 요청만 하위 서비스로 내려보내고,
// 하위 서비스(order/product)는 토큰을 다시 검사하지 않는다(클러스터 내부 신뢰).
// 검증 공개키는 auth-service 의 /.well-known/jwks.json 에서 받아온다 — 비밀키 공유가 없다.
@Configuration
class SecurityConfig(
    @Value("\${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") private val jwkSetUri: String,
) {
    companion object {
        // 슈퍼앱 IdP(auth-service)가 "이 은행 API 용"으로 발급할 때 넣는 audience 값.
        // auth-service 의 JwtIssuer.BANK_AUDIENCE 와 반드시 같은 문자열이어야 한다 — 두 모듈이
        // 서로를 의존하지 않는 별도 시스템이라 상수를 공유하지 않고 값으로만 맞춘다(실서비스의
        // 슈퍼앱-은행 연계와 동일한 상황: 상수가 아니라 "연계 규약 문서"로 값을 맞춘다).
        const val EXPECTED_AUDIENCE = "https://api.n2soft-bank.internal"
    }

    // 서명·만료 검증(JwtValidators 기본)에 audience 검증을 더한다.
    // 이게 없으면 같은 IdP 가 "다른 제휴사용"으로 발급한, 서명은 진짜인 토큰도 통과해버린다
    // — 서명 검증만으로는 "누구에게 준 토큰인가"를 못 걸러낸다.
    @Bean
    fun jwtDecoder(): JwtDecoder {
        val decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
        val audienceValidator = OAuth2TokenValidator<Jwt> { jwt ->
            if (jwt.audience.contains(EXPECTED_AUDIENCE)) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(
                    OAuth2Error("invalid_token", "이 은행 시스템(aud=$EXPECTED_AUDIENCE) 용으로 발급된 토큰이 아닙니다", null),
                )
            }
        }
        decoder.setJwtValidator(DelegatingOAuth2TokenValidator(JwtValidators.createDefault(), audienceValidator))
        return decoder
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    // 로그인과 상태 확인은 토큰 없이 — 그 외 /api/** 는 전부 토큰 필수
                    .requestMatchers("/api/auth/**", "/api/health", "/actuator/**").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt { } }
        return http.build()
    }

    // 랩 전용 — 다른 포트의 React dev 서버가 호출할 수 있게 전부 연다 (Security 체인용 CORS 소스)
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOriginPatterns = listOf("*")
            allowedMethods = listOf("GET", "POST", "OPTIONS")
            allowedHeaders = listOf("*")
            exposedHeaders = listOf("X-Action-Id")
        }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/api/**", config) }
    }
}
