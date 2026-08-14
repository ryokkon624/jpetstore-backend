package com.example.jpetstore.backend.infrastructure.security

import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.web.csrf.CsrfToken
import spock.lang.Specification

/**
 * AC3: Spring Security の CSRF トークンは既定で遅延解決（Supplier）されるため、何も
 * {@code CsrfToken#getToken()} を呼ばないと Set-Cookie（XSRF-TOKEN）が発行されない。
 * SPA 向けに毎リクエストで強制的に解決させるフィルタ（公式パターン）。
 */
class CsrfCookieFilterSpec extends Specification {

    def filter = new CsrfCookieFilter()

    def "リクエスト属性にCsrfTokenがあればgetToken()を呼んで強制解決させる"() {
        given:
        def request = new MockHttpServletRequest()
        def csrfToken = Mock(CsrfToken)
        request.setAttribute(CsrfToken.class.name, csrfToken)
        def response = new MockHttpServletResponse()
        def chain = new MockFilterChain()

        when:
        filter.doFilter(request, response, chain)

        then:
        1 * csrfToken.getToken()
        chain.request != null
    }

    def "CsrfToken属性が無くても例外にならずchainを継続する"() {
        given:
        def request = new MockHttpServletRequest()
        def response = new MockHttpServletResponse()
        def chain = new MockFilterChain()

        when:
        filter.doFilter(request, response, chain)

        then:
        noExceptionThrown()
        chain.request != null
    }
}
