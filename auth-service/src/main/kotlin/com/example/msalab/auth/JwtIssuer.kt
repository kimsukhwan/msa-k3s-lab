package com.example.msalab.auth

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.Date
import java.util.UUID

// 슈퍼앱 IdP 모사 — RS256 JWT 발급기. 기동 시 RSA 키쌍을 만들고 공개키만 JWKS 로 노출한다.
// 검증측(은행 gateway)은 이 JWKS 를 받아 서명을 검증하므로 비밀키를 공유할 필요가 없다(JWKS 패턴의 요점).
// 키가 메모리에만 있으므로 replica 는 반드시 1 — 늘리려면 키를 공유 저장소에 두고 회전 설계가 필요하다.
@Component
class JwtIssuer {
    companion object {
        /** 은행 시스템용 수신자 표시 — 검증측이 이 값을 확인해야 "다른 제휴사용 토큰 재사용"이 막힌다. */
        const val BANK_AUDIENCE = "bank-api"
    }

    private val rsaKey = RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate()
    private val signer = RSASSASigner(rsaKey)

    val tokenTtl: Duration = Duration.ofMinutes(30)

    /** 슈퍼앱 고객키(custKey)를 실은 액세스 토큰을 발급한다. audience 가 곧 "누구에게 줄 토큰인가"다. */
    fun issue(username: String, custKey: String, audience: String = BANK_AUDIENCE): String {
        val now = Date()
        val claims = JWTClaimsSet.Builder()
            .subject(username)
            .issuer("superapp-idp")
            .audience(audience)
            .claim("custKey", custKey)
            .issueTime(now)
            .expirationTime(Date(now.time + tokenTtl.toMillis()))
            .build()
        val jwt = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.keyID).type(JOSEObjectType.JWT).build(),
            claims,
        )
        jwt.sign(signer)
        return jwt.serialize()
    }

    /** 공개키 셋(JWKS) — 검증측(gateway)이 주기적으로 받아 간다. */
    fun jwks(): Map<String, Any> = JWKSet(rsaKey.toPublicJWK()).toJSONObject()
}
