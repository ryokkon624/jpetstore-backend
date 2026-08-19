package com.example.jpetstore.backend.infrastructure.security

import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.support.TestJwtSecrets
import spock.lang.Specification

import java.time.Duration

/**
 * レビュー指摘対応: access/refresh トークンは {@code typ} claim で型を区別する。
 * refresh を access として（あるいはその逆）誤って使えないことを実証する。
 */
class JwtServiceSpec extends Specification {

    def properties = new JwtProperties(TestJwtSecrets.STRONG, Duration.ofMinutes(15), Duration.ofDays(7))
    def service = new JwtService(properties)

    def "generateAccessTokenで発行したトークンをparseAccessTokenで復元できる"() {
        given:
        def user = new AuthenticatedUser(42L, "j2ee", ["USER", "ADMIN"])

        when:
        def token = service.generateAccessToken(user)
        def parsed = service.parseAccessToken(token)

        then:
        parsed.isPresent()
        parsed.get() == user
    }

    def "generateRefreshTokenで発行したトークンをparseRefreshTokenで復元できる"() {
        given:
        def user = new AuthenticatedUser(1L, "j2ee", [])

        when:
        def token = service.generateRefreshToken(user)
        def parsed = service.parseRefreshToken(token)

        then:
        parsed.isPresent()
        parsed.get() == user
    }

    def "refresh tokenをparseAccessTokenに渡すと空を返す(型混同拒否)"() {
        given:
        def user = new AuthenticatedUser(1L, "j2ee", ["USER"])
        def refreshToken = service.generateRefreshToken(user)

        expect:
        service.parseAccessToken(refreshToken).isEmpty()
    }

    def "access tokenをparseRefreshTokenに渡すと空を返す(型混同拒否)"() {
        given:
        def user = new AuthenticatedUser(1L, "j2ee", ["USER"])
        def accessToken = service.generateAccessToken(user)

        expect:
        service.parseRefreshToken(accessToken).isEmpty()
    }

    def "改ざんされたトークンはparseAccessTokenで空を返す"() {
        given:
        def user = new AuthenticatedUser(1L, "j2ee", ["USER"])
        def token = service.generateAccessToken(user)
        def tampered = token.substring(0, token.length() - 2) + "xx"

        when:
        def parsed = service.parseAccessToken(tampered)

        then:
        parsed.isEmpty()
    }

    def "不正な形式の文字列はparseAccessTokenで空を返す"() {
        expect:
        service.parseAccessToken("not-a-jwt").isEmpty()
    }

    def "異なる鍵で署名されたトークンはparseAccessTokenで空を返す"() {
        given:
        def otherSecret = "zZ9yY8xX7wW6vV5uU4tT3sS2rR1qQ0pP"
        def otherService = new JwtService(new JwtProperties(otherSecret, Duration.ofMinutes(15), Duration.ofDays(7)))
        def token = otherService.generateAccessToken(new AuthenticatedUser(1L, "j2ee", []))

        when:
        def parsed = service.parseAccessToken(token)

        then:
        parsed.isEmpty()
    }

    def "期限切れトークンはparseAccessTokenで空を返す"() {
        given:
        def expiredProperties = new JwtProperties(TestJwtSecrets.STRONG, Duration.ofSeconds(-1), Duration.ofDays(7))
        def expiredService = new JwtService(expiredProperties)
        def token = expiredService.generateAccessToken(new AuthenticatedUser(1L, "j2ee", []))

        when:
        def parsed = service.parseAccessToken(token)

        then:
        parsed.isEmpty()
    }
}
