package com.example.msalab.gateway

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * jti(토큰 고유 ID) 폐기 목록 — 로그아웃/기기 도난 신고 등으로 만료 전에 토큰을 즉시 무효화한다.
 *
 * JWT는 태생적으로 무상태라 서명·aud·exp 검증만으로는 "발급 후 취소"가 안 된다 — 탈취된 토큰은
 * 만료 시각까지 계속 유효하다. 이 목록이 그 빈틈을 메우는 추가 방어선이다(1차 방어선은 여전히
 * 서명·aud 검증).
 *
 * Redis가 응답하지 않으면 fail-open(통과)으로 처리한다 — 폐기 목록은 부가 방어선이라 가용성을
 * 우선했다. 반대로 CustomerKeyResolver(외부 키→내부 키 변환)는 fail-closed다: 그쪽은 "누구인지
 * 모르는 요청을 처리해버리는" 더 큰 사고로 이어지지만, 여기는 "폐기됐어야 할 토큰이 아주 잠깐
 * 더 유효한" 정도라 트레이드오프가 다르다. 실 운영에서는 이 결정을 감사 로그와 함께 명시해야 한다.
 */
@Component
class TokenRevocationService(private val redis: StringRedisTemplate) {
    private val log = LoggerFactory.getLogger(TokenRevocationService::class.java)

    fun isRevoked(jti: String): Boolean =
        runCatching { redis.hasKey(key(jti)) == true }
            .getOrElse {
                log.warn("폐기 목록 조회 실패 — fail-open 으로 통과시킴 jti={} ({})", jti, it.message)
                false
            }

    /** 남은 토큰 수명만큼만 폐기 기록을 남긴다 — 이미 만료된 토큰은 등록할 필요가 없다. */
    fun revoke(jti: String, remaining: Duration) {
        if (remaining.isZero || remaining.isNegative) return
        runCatching { redis.opsForValue().set(key(jti), "revoked", remaining) }
            .onFailure { log.warn("폐기 등록 실패 jti={} ({})", jti, it.message) }
    }

    private fun key(jti: String) = "revoked-jti:$jti"
}
