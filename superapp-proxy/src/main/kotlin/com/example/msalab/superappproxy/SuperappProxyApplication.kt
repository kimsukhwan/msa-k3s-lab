package com.example.msalab.superappproxy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

/**
 * 슈퍼앱 백엔드 역할을 흉내내는 서비스.
 *
 * 실제 구조는 "슈퍼앱 최종사용자 앱 → 슈퍼앱 백엔드 → (mTLS) → 은행 gateway" 이고, mTLS는
 * 서버-서버 구간에 건다 — 브라우저가 클라이언트 인증서를 직접 제시하는 UX는 OS 인증서 선택
 * 팝업이 뜨는 등 이 데모 화면과 안 맞을 뿐 아니라, 애초에 실제 구조와도 다르다.
 *
 * 그래서 React(브라우저, 슈퍼앱의 최종사용자 앱 대역)는 지금까지처럼 평범한 HTTP로 이 서비스를
 * 부르고, 이 서비스가 슈퍼앱 백엔드의 클라이언트 인증서를 들고 은행 gateway 의 mTLS 포트로
 * 넘어간다 — React 쪽 코드는 한 줄도 안 바뀐다(Ingress 배선만 gateway 대신 여기로 바뀐다).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
class SuperappProxyApplication

fun main(args: Array<String>) {
    runApplication<SuperappProxyApplication>(*args)
}
