package com.example.msalab.superappproxy

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mtls")
data class MtlsProperties(
    val bankGatewayUrl: String,
    val keystorePath: String,
    val keystorePassword: String,
    val truststorePath: String,
    val truststorePassword: String,
)
