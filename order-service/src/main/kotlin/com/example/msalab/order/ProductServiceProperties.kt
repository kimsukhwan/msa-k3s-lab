package com.example.msalab.order

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "product-service")
data class ProductServiceProperties(val baseUrl: String)
