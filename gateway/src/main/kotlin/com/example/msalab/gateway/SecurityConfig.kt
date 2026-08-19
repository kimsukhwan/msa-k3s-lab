package com.example.msalab.gateway

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

// 인증 경계는 gateway 하나다 — 여기서 JWT(JWKS 서명 검증)를 통과한 요청만 하위 서비스로 내려보내고,
// 하위 서비스(order/product)는 토큰을 다시 검사하지 않는다(클러스터 내부 신뢰).
// 검증 공개키는 auth-service 의 /.well-known/jwks.json 에서 받아온다 — 비밀키 공유가 없다.
@Configuration
class SecurityConfig {

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
