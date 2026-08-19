package com.example.msalab.gateway

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "backend")
data class BackendProperties(val orderServiceUrl: String, val productServiceUrl: String)
