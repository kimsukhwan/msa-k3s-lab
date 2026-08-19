package com.example.msalab.order

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.client.RestClient

@SpringBootApplication
@ConfigurationPropertiesScan
class OrderServiceApplication {

    @Bean
    fun productServiceClient(props: ProductServiceProperties): RestClient =
        RestClient.builder()
            .baseUrl(props.baseUrl)
            .requestInterceptor(CorrelationPropagationInterceptor())
            .build()
}

fun main(args: Array<String>) {
    runApplication<OrderServiceApplication>(*args)
}
