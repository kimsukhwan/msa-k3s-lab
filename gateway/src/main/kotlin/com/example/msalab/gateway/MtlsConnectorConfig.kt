package com.example.msalab.gateway

import org.apache.catalina.connector.Connector
import org.apache.coyote.http11.Http11NioProtocol
import org.apache.tomcat.util.net.SSLHostConfig
import org.apache.tomcat.util.net.SSLHostConfigCertificate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.stereotype.Component

/**
 * gateway는 포트가 두 개다.
 *  - server.port(평문 HTTP, 8093) — k8s 헬스체크 전용. PlainPortRestrictionFilter가
 *    업무 경로(주문·상품 조회·로그인 등)는 여기서 전부 막는다.
 *  - mtls.port(HTTPS + 클라이언트 인증서 필수, 8446) — 실제 업무 트래픽은 전부 이쪽으로만 들어온다.
 *
 * 지금까지의 JWT(서명·aud·jti)가 "이 요청이 진짜 고객의 것인가"를 증명한다면, 이 mTLS 포트는
 * "이 연결이 진짜 슈퍼앱의 서버에서 온 것인가"를 증명한다 — 서로 다른 질문에 대한 답이다.
 * 서명이 완벽히 유효한 JWT를 들고 있어도, superapp-proxy의 개인키(클라이언트 인증서) 없이는
 * 이 포트에 TCP 연결(TLS 핸드셰이크)조차 맺어지지 않는다 — HTTP 요청이 오가기 전에 끝난다.
 *
 * mtls.enabled 가 false(로컬 개발 기본값)면 추가 커넥터를 만들지 않는다 — 인증서 없이도
 * ./gradlew :gateway:bootRun 이 그대로 동작하게 하기 위함이다.
 */
@Component
class MtlsConnectorConfig(
    @Value("\${mtls.enabled:false}") private val mtlsEnabled: Boolean,
    @Value("\${mtls.port:8446}") private val mtlsPort: Int,
    @Value("\${mtls.keystore-path:}") private val keystorePath: String,
    @Value("\${mtls.keystore-password:}") private val keystorePassword: String,
    @Value("\${mtls.truststore-path:}") private val truststorePath: String,
    @Value("\${mtls.truststore-password:}") private val truststorePassword: String,
) : WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private val log = LoggerFactory.getLogger(MtlsConnectorConfig::class.java)

    override fun customize(factory: TomcatServletWebServerFactory) {
        if (!mtlsEnabled) {
            log.info("mTLS 비활성(mtls.enabled=false) — 추가 커넥터를 만들지 않는다")
            return
        }
        require(keystorePath.isNotBlank() && truststorePath.isNotBlank()) {
            "mtls.enabled=true 인데 keystore-path/truststore-path 가 비어 있다 — fail-fast"
        }

        val connector = Connector(Http11NioProtocol::class.java.name)
        connector.port = mtlsPort
        connector.scheme = "https"
        connector.secure = true
        connector.setProperty("SSLEnabled", "true")

        val sslHostConfig = SSLHostConfig()
        // "required" — 클라이언트 인증서 제시를 필수로 만드는, mTLS의 핵심 설정 한 줄.
        // 인증서를 안 보내거나 CA가 다른 인증서를 보내면 TLS 핸드셰이크 자체가 실패한다.
        sslHostConfig.setCertificateVerification("required")
        sslHostConfig.truststoreFile = truststorePath
        sslHostConfig.truststorePassword = truststorePassword
        sslHostConfig.truststoreType = "PKCS12"

        val certificate = SSLHostConfigCertificate(sslHostConfig, SSLHostConfigCertificate.Type.RSA)
        certificate.certificateKeystoreFile = keystorePath
        certificate.certificateKeystorePassword = keystorePassword
        certificate.certificateKeystoreType = "PKCS12"
        sslHostConfig.addCertificate(certificate) // 생성자만으로는 등록되지 않는다 — 명시 호출 필요

        connector.addSslHostConfig(sslHostConfig)
        factory.addAdditionalTomcatConnectors(connector)
        log.info("mTLS 커넥터 활성화 — port={} (클라이언트 인증서 필수)", mtlsPort)
    }
}
