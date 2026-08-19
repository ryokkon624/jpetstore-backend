package com.example.jpetstore.backend.infrastructure.security

import com.example.jpetstore.backend.support.TestJwtSecrets
import spock.lang.Specification

import java.time.Duration

/**
 * AC5/AC-neg1 (SBD-11): JWT 署名鍵の最小鍵長検証（起動時 fail-fast）。
 *
 * <p>「未設定なら起動失敗」は Spring のプレースホルダ未解決で担保される
 * （{@link com.example.jpetstore.backend.config.ApplicationBootFailFastSpec} で実証）。
 * denylist・最小エントロピー検証（#38 AC1/AC2）は {@link JwtSecretPolicySpec} が単体で担う。
 * 本テストは「設定されているが短すぎる」ケースの検証を担う。
 */
class JwtPropertiesSpec extends Specification {

    private static final Duration ACCESS_TTL = Duration.ofMinutes(15)
    private static final Duration REFRESH_TTL = Duration.ofDays(7)

    def "32byte以上・エントロピー十分な鍵なら正常に構築できる"() {
        given:
        String secret = TestJwtSecrets.STRONG

        when:
        def properties = new JwtProperties(secret, ACCESS_TTL, REFRESH_TTL)

        then:
        properties.signingKey() != null
        properties.accessTokenTtl() == ACCESS_TTL
        properties.refreshTokenTtl() == REFRESH_TTL
    }

    def "32byte未満の鍵は起動時に例外を投げる(最小鍵長検証)"() {
        given:
        String secret = "a" * 31

        when:
        new JwtProperties(secret, ACCESS_TTL, REFRESH_TTL)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("32")
    }
}
