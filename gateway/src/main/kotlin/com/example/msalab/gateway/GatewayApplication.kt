package com.example.msalab.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.client.RestClient

@SpringBootApplication
@ConfigurationPropertiesScan
class GatewayApplication {

    @Bean("orderServiceClient")
    fun orderServiceClient(props: BackendProperties): RestClient =
        RestClient.builder()
            .baseUrl(props.orderServiceUrl)
            .requestInterceptor(CorrelationPropagationInterceptor())
            .build()

    @Bean("productServiceClient")
    fun productServiceClient(props: BackendProperties): RestClient =
        RestClient.builder()
            .baseUrl(props.productServiceUrl)
            .requestInterceptor(CorrelationPropagationInterceptor())
            .build()

    @Bean("authServiceClient")
    fun authServiceClient(props: BackendProperties): RestClient =
        RestClient.builder()
            .baseUrl(props.authServiceUrl)
            .requestInterceptor(CorrelationPropagationInterceptor())
            .build()
}

fun main(args: Array<String>) {
    runApplication<GatewayApplication>(*args)
}
