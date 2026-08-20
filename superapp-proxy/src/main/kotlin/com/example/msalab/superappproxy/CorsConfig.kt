package com.example.msalab.superappproxy

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

// 이제 브라우저가 직접 마주치는 첫 관문이 gateway 대신 이 서비스다 — 랩 전용으로 전부 연다.
@Configuration
class CorsConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**").allowedOriginPatterns("*").allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("*").exposedHeaders("X-Action-Id", "X-Cust-Id")
    }
}
