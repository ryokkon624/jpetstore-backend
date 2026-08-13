package com.example.jpetstore.backend.infrastructure.security

import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification

import java.time.Duration

/**
 * AC3: httpOnly Cookie から access token を読み取り SecurityContext に認証情報をセットするフィルタ。
 */
class JwtAuthenticationFilterSpec extends Specification {

    def properties = new JwtProperties("a" * 32, Duration.ofMinutes(15), Duration.ofDays(7))
    def jwtService = new JwtService(properties)
    def cookieSupport = new AuthCookieSupport(true, "Strict")
    def filter = new JwtAuthenticationFilter(jwtService, cookieSupport)

    void cleanup() {
        SecurityContextHolder.clearContext()
    }

    def "有効なaccess tokenのCookieがあればSecurityContextにAuthenticatedUserと権限をセットする"() {
        given:
        def user = new AuthenticatedUser(7L, "j2ee", ["USER", "ADMIN"])
        def token = jwtService.generateAccessToken(user)
        def request = new MockHttpServletRequest()
        request.setCookies(new jakarta.servlet.http.Cookie(AuthCookieSupport.ACCESS_TOKEN_COOKIE, token))
        def response = new MockHttpServletResponse()
        def chain = new MockFilterChain()

        when:
        filter.doFilter(request, response, chain)

        then:
        def authentication = SecurityContextHolder.getContext().authentication
        authentication != null
        authentication.principal == user
        authentication.authorities*.authority.toSet() == ["ROLE_USER", "ROLE_ADMIN"].toSet()
        chain.request != null // フィルタチェーンが継続していること
    }

    def "access tokenのCookieが無ければSecurityContextに何もセットせずchainを継続する"() {
        given:
        def request = new MockHttpServletRequest()
        def response = new MockHttpServletResponse()
        def chain = new MockFilterChain()

        when:
        filter.doFilter(request, response, chain)

        then:
        SecurityContextHolder.getContext().authentication == null
        chain.request != null
    }

    def "改ざんされたaccess tokenのCookieならSecurityContextに何もセットせずchainを継続する(例外を投げない)"() {
        given:
        def request = new MockHttpServletRequest()
        request.setCookies(new jakarta.servlet.http.Cookie(AuthCookieSupport.ACCESS_TOKEN_COOKIE, "tampered.token.value"))
        def response = new MockHttpServletResponse()
        def chain = new MockFilterChain()

        when:
        filter.doFilter(request, response, chain)

        then:
        noExceptionThrown()
        SecurityContextHolder.getContext().authentication == null
        chain.request != null
    }
}
