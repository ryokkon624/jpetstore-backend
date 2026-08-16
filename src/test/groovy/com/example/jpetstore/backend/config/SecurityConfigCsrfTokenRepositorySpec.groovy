package com.example.jpetstore.backend.config

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.web.csrf.DefaultCsrfToken
import spock.lang.Specification

/**
 * #6 AC1(SBD-15・計画フェーズ確定①): XSRF-TOKEN Cookie自体にSameSite/Secureを付与する
 * （{@link SecurityConfig#csrfTokenRepository}・{@code CookieCsrfTokenRepository#setCookieCustomizer}）。
 * JWT Cookie（access/refresh）は既にStrict/Secure/HttpOnly（{@code AuthCookieSupport}）であり、XSRF-TOKEN
 * Cookieも同じ属性値（{@code jwt.cookie.same-site}/{@code jwt.cookie.secure}、既定Strict/Secure）へ揃える。
 *
 * <p>MockMvc経由の統合テストにはしない: (1) {@code SecurityMockMvcRequestPostProcessors.csrf()}は
 * 呼ばれた時点で共有Springコンテキストの{@code CsrfTokenRepository}をセッションベースへ恒久的に差し替える
 * （テストスイート全体でリークする既知の挙動）ため、他のCSRFテストと同一コンテキストで実行すると
 * Cookieが発行されなくなる。(2) {@code MockHttpServletResponse}のSet-Cookieヘッダ再構築は
 * {@code SameSite}を{@code MockCookie}型のみ見るため、{@code CookieCsrfTokenRepository}が生成する素の
 * {@code jakarta.servlet.http.Cookie}のSameSite属性はヘッダ文字列に反映されない。
 * 本テストはリポジトリを直接構築しCookieオブジェクトの属性を検証することでこの2点を回避する
 * （{@code CsrfCookieFilterSpec}と同じくSpringコンテキスト不要のplain Specification）。
 */
class SecurityConfigCsrfTokenRepositorySpec extends Specification {

    def securityConfig = new SecurityConfig()

    def "既定値(secure=true, same-site=Strict)でXSRF-TOKEN CookieにSecure+SameSite=Strictを付与しhttpOnlyはfalseのまま"() {
        given:
        def repository = securityConfig.csrfTokenRepository(true, "Strict")
        def request = new MockHttpServletRequest()
        def response = new MockHttpServletResponse()
        def token = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "test-token-value")

        when:
        repository.saveToken(token, request, response)

        then:
        def cookie = response.getCookie("XSRF-TOKEN")
        cookie != null
        cookie.getAttribute("SameSite") == "Strict"
        cookie.getSecure()
        !cookie.isHttpOnly()
    }

    def "jwt.cookie設定値を差し替えると(secure=false, same-site=Lax)XSRF-TOKEN Cookieにも反映される(same-site環境設定可)"() {
        given:
        def repository = securityConfig.csrfTokenRepository(false, "Lax")
        def request = new MockHttpServletRequest()
        def response = new MockHttpServletResponse()
        def token = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "test-token-value")

        when:
        repository.saveToken(token, request, response)

        then:
        def cookie = response.getCookie("XSRF-TOKEN")
        cookie.getAttribute("SameSite") == "Lax"
        !cookie.getSecure()
    }
}
