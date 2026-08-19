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

// RS256 JWT 발급기. 기동 시 RSA 키쌍을 만들고 공개키만 JWKS 로 노출한다 —
// gateway 는 이 JWKS 를 받아 서명을 검증하므로 비밀키를 공유할 필요가 없다(JWKS 패턴의 요점).
// 키가 메모리에만 있으므로 replica 는 반드시 1 — 늘리려면 키를 공유 저장소에 두고 회전 설계가 필요하다.
@Component
class JwtIssuer {
    private val rsaKey = RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate()
    private val signer = RSASSASigner(rsaKey)

    val tokenTtl: Duration = Duration.ofMinutes(30)

    /** username 을 sub 로 하는 액세스 토큰을 발급한다. */
    fun issue(username: String): String {
        val now = Date()
        val claims = JWTClaimsSet.Builder()
            .subject(username)
            .issuer("auth-service")
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
