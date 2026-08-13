package com.example.jpetstore.backend.infrastructure.security

import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import spock.lang.Specification

import java.time.Duration

class JwtServiceSpec extends Specification {

    def properties = new JwtProperties("a" * 32, Duration.ofMinutes(15), Duration.ofDays(7))
    def service = new JwtService(properties)

    def "generateAccessTokenで発行したトークンをparseTokenで復元できる"() {
        given:
        def user = new AuthenticatedUser(42L, "j2ee", ["USER", "ADMIN"])

        when:
        def token = service.generateAccessToken(user)
        def parsed = service.parseToken(token)

        then:
        parsed.isPresent()
        parsed.get() == user
    }

    def "generateRefreshTokenで発行したトークンもparseTokenで復元できる"() {
        given:
        def user = new AuthenticatedUser(1L, "j2ee", [])

        when:
        def token = service.generateRefreshToken(user)
        def parsed = service.parseToken(token)

        then:
        parsed.isPresent()
        parsed.get() == user
    }

    def "改ざんされたトークンはparseTokenで空を返す"() {
        given:
        def user = new AuthenticatedUser(1L, "j2ee", ["USER"])
        def token = service.generateAccessToken(user)
        def tampered = token.substring(0, token.length() - 2) + "xx"

        when:
        def parsed = service.parseToken(tampered)

        then:
        parsed.isEmpty()
    }

    def "不正な形式の文字列はparseTokenで空を返す"() {
        expect:
        service.parseToken("not-a-jwt").isEmpty()
    }

    def "異なる鍵で署名されたトークンはparseTokenで空を返す"() {
        given:
        def otherService = new JwtService(new JwtProperties("b" * 32, Duration.ofMinutes(15), Duration.ofDays(7)))
        def token = otherService.generateAccessToken(new AuthenticatedUser(1L, "j2ee", []))

        when:
        def parsed = service.parseToken(token)

        then:
        parsed.isEmpty()
    }

    def "期限切れトークンはparseTokenで空を返す"() {
        given:
        def expiredProperties = new JwtProperties("a" * 32, Duration.ofSeconds(-1), Duration.ofDays(7))
        def expiredService = new JwtService(expiredProperties)
        def token = expiredService.generateAccessToken(new AuthenticatedUser(1L, "j2ee", []))

        when:
        def parsed = service.parseToken(token)

        then:
        parsed.isEmpty()
    }
}
