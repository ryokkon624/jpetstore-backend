package com.example.jpetstore.backend.infrastructure.security

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification

import java.time.Duration

class AuthCookieSupportSpec extends Specification {

    def support = new AuthCookieSupport(true, "Strict")

    def "writeAccessTokenCookieはHttpOnly/Secure/SameSite/Path付きでSet-Cookieヘッダを書く"() {
        given:
        def response = new MockHttpServletResponse()

        when:
        support.writeAccessTokenCookie(response, "token-value", Duration.ofMinutes(15))

        then:
        def header = response.getHeader("Set-Cookie")
        header.contains("ACCESS_TOKEN=token-value")
        header.contains("HttpOnly")
        header.contains("Secure")
        header.contains("SameSite=Strict")
        header.contains("Path=/")
        header.contains("Max-Age=900")
    }

    def "writeRefreshTokenCookieも同様に書く"() {
        given:
        def response = new MockHttpServletResponse()

        when:
        support.writeRefreshTokenCookie(response, "refresh-value", Duration.ofDays(7))

        then:
        def header = response.getHeader("Set-Cookie")
        header.contains("REFRESH_TOKEN=refresh-value")
        header.contains("Max-Age=604800")
    }

    def "clearAuthCookiesは両Cookieを即時失効させる(Max-Age=0)"() {
        given:
        def response = new MockHttpServletResponse()

        when:
        support.clearAuthCookies(response)

        then:
        def headers = response.getHeaders("Set-Cookie")
        headers.size() == 2
        headers.every { it.contains("Max-Age=0") }
        headers.any { it.startsWith("ACCESS_TOKEN=") }
        headers.any { it.startsWith("REFRESH_TOKEN=") }
    }

    def "readCookieは指定名のCookie値を返す"() {
        given:
        def request = new MockHttpServletRequest()
        request.setCookies(new jakarta.servlet.http.Cookie("ACCESS_TOKEN", "abc123"))

        expect:
        support.readCookie(request, "ACCESS_TOKEN") == Optional.of("abc123")
        support.readCookie(request, "NOT_EXIST") == Optional.empty()
    }

    def "Cookieが1つも無いリクエストではreadCookieは空を返す"() {
        given:
        def request = new MockHttpServletRequest()

        expect:
        support.readCookie(request, "ACCESS_TOKEN") == Optional.empty()
    }
}
