package com.example.msalab.gateway

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

// 학습용 랩 전용 설정 — 정적 HTML 프론트엔드(다른 포트)가 fetch()로 호출할 수 있도록 CORS를 전부 연다.
@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**").allowedOrigins("*").allowedMethods("GET")
    }
}
