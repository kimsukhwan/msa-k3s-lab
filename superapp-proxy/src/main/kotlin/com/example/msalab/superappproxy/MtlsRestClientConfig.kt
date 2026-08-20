package com.example.msalab.superappproxy

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.io.FileInputStream
import java.net.http.HttpClient
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * 은행 gateway 의 mTLS 포트로 나갈 때만 쓰는 RestClient — 이 서비스가 "슈퍼앱 백엔드"라는
 * 신원을 증명하는 클라이언트 인증서를 TLS 핸드셰이크에 싣는다.
 *
 * mTLS 는 상호(mutual) 인증이다 — 이 서비스도 gateway 의 서버 인증서가 우리가 신뢰하는
 * CA(이 랩의 mTLS 전용 CA)가 서명한 게 맞는지 검증해야 한다(trustManagers). 클라이언트 인증서만
 * 보내고 서버 검증을 생략하면, 가짜 gateway 에게 요청을 그대로 넘겨버리는 것과 같다.
 */
@Configuration
class MtlsRestClientConfig(private val props: MtlsProperties) {

    @Bean
    fun bankGatewayClient(): RestClient {
        val sslContext = buildSslContext()
        val httpClient = HttpClient.newBuilder().sslContext(sslContext).build()
        return RestClient.builder()
            .baseUrl(props.bankGatewayUrl)
            .requestFactory(JdkClientHttpRequestFactory(httpClient))
            .build()
    }

    private fun buildSslContext(): SSLContext {
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            FileInputStream(props.keystorePath).use { load(it, props.keystorePassword.toCharArray()) }
        }
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, props.keystorePassword.toCharArray())
        }
        val trustStore = KeyStore.getInstance("PKCS12").apply {
            FileInputStream(props.truststorePath).use { load(it, props.truststorePassword.toCharArray()) }
        }
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(trustStore)
        }
        return SSLContext.getInstance("TLS").apply {
            init(keyManagerFactory.keyManagers, trustManagerFactory.trustManagers, SecureRandom())
        }
    }
}
