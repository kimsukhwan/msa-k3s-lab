package com.example.msalab.superappproxy

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient

// 프록시가 그대로 실어 보내면 안 되는 hop-by-hop 헤더 — HttpClient 가 자기 값으로 새로 계산한다.
private val EXCLUDED_REQUEST_HEADERS = setOf("host", "content-length", "connection")
private val EXCLUDED_RESPONSE_HEADERS = setOf("transfer-encoding", "connection")

/**
 * 받은 요청(메서드·경로·헤더·바디)을 그대로 은행 gateway 의 mTLS 포트로 넘기는 범용 패스스루.
 *
 * 이 서비스는 업무 로직을 전혀 모른다 — 로그인이든 주문이든 로그아웃이든 경로 하나하나를
 * 알 필요 없이 그대로 전달한다(Authorization 헤더도 그대로 실려가므로 JWT 인증은 gateway가
 * 변함없이 처리한다). 여기서 증명하는 것은 딱 하나, "이 연결이 슈퍼앱 백엔드에서 왔다"는
 * 사실뿐이고 그건 RestClient 가 쓰는 TLS 클라이언트 인증서가 한다.
 */
@RestController
class ProxyController(private val bankGatewayClient: RestClient) {

    @RequestMapping("/api/**")
    fun proxy(request: HttpServletRequest): ResponseEntity<ByteArray> {
        val path = request.requestURI + (request.queryString?.let { "?$it" } ?: "")
        val body = request.inputStream.readBytes()

        val spec = bankGatewayClient.method(HttpMethod.valueOf(request.method)).uri(path)
        request.headerNames.asIterator().forEach { name ->
            if (name.lowercase() !in EXCLUDED_REQUEST_HEADERS) {
                spec.header(name, request.getHeader(name))
            }
        }
        if (body.isNotEmpty()) spec.body(body)

        return spec.exchange { _, response ->
            val headers = HttpHeaders()
            response.headers.forEach { name, values ->
                if (name.lowercase() !in EXCLUDED_RESPONSE_HEADERS) headers[name] = values
            }
            ResponseEntity.status(response.statusCode)
                .headers(headers)
                .body(response.body.readBytes())
        } ?: ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ByteArray(0))
    }
}
