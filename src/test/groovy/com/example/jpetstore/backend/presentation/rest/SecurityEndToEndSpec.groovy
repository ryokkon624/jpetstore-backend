package com.example.jpetstore.backend.presentation.rest

import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.infrastructure.security.AuthCookieSupport
import com.example.jpetstore.backend.infrastructure.security.JwtService
import com.example.jpetstore.backend.support.IntegrationTestBase
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Tag

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * secure-by-default 基盤の end-to-end 実証（AC2/AC3/AC4/AC7/AC8）。
 * 実際の Spring Security フィルタチェーン（JWT認証・CSRF・例外正規化・監査ログ記録）を
 * {@code /api/secured/ping}（唯一の保護テストエンドポイント）と {@code /api/auth/refresh} 経由で検証する。
 */
@Tag("integration")
@AutoConfigureMockMvc
class SecurityEndToEndSpec extends IntegrationTestBase {

    @Autowired
    MockMvc mockMvc

    @Autowired
    JwtService jwtService

    @Autowired
    JdbcTemplate jdbcTemplate

    void setup() {
        jdbcTemplate.update("DELETE FROM t_audit_log")
    }

    private Cookie accessTokenCookie(List<String> roles) {
        def user = new AuthenticatedUser(1L, "j2ee", roles)
        new Cookie(AuthCookieSupport.ACCESS_TOKEN_COOKIE, jwtService.generateAccessToken(user))
    }

    def "未認証(Cookie無し)でsecured/pingを叩くと401かつ監査ログに記録される"() {
        when:
        def result = mockMvc.perform(get("/api/secured/ping"))

        then:
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath('$.code').value("UNAUTHORIZED"))

        and:
        jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_audit_log WHERE event_type='AUTHZ_FAILURE' AND result='DENIED'",
                Integer) == 1
    }

    def "USERロールでADMIN限定エンドポイントを叩くと403かつ監査ログに記録される"() {
        when:
        def result = mockMvc.perform(get("/api/secured/ping").cookie(accessTokenCookie(["USER"])))

        then:
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath('$.code').value("FORBIDDEN"))

        and:
        jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_audit_log WHERE event_type='AUTHZ_FAILURE' AND result='DENIED'",
                Integer) == 1
    }

    def "ADMINロールならsecured/pingは200を返す"() {
        expect:
        mockMvc.perform(get("/api/secured/ping").cookie(accessTokenCookie(["ADMIN"])))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.status').value("ok"))
    }

    def "simulateError=notFoundは404に正規化される"() {
        expect:
        mockMvc.perform(get("/api/secured/ping?simulateError=notFound").cookie(accessTokenCookie(["ADMIN"])))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath('$.code').value("NOT_FOUND"))
    }

    def "simulateError=conflictは409に正規化される(AC8)"() {
        expect:
        mockMvc.perform(get("/api/secured/ping?simulateError=conflict").cookie(accessTokenCookie(["ADMIN"])))
                .andExpect(status().isConflict())
                .andExpect(jsonPath('$.code').value("CONFLICT"))
    }

    def "simulateError=unexpectedは500に正規化されスタックトレース詳細を含まない"() {
        when:
        def result = mockMvc.perform(get("/api/secured/ping?simulateError=unexpected").cookie(accessTokenCookie(["ADMIN"])))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath('$.code').value("INTERNAL_ERROR"))
                .andReturn()

        then:
        !result.response.contentAsString.contains("/internal/secret/path.java")
    }

    def "CSRFトークン無しでPOST /api/auth/refreshすると403で拒否される"() {
        expect:
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isForbidden())
    }

    def "CSRFトークンありでもrefresh Cookieが無ければ401になる"() {
        expect:
        mockMvc.perform(post("/api/auth/refresh").with(csrf()))
                .andExpect(status().isUnauthorized())
    }

    def "有効なrefresh CookieならPOST /api/auth/refreshで新しいACCESS_TOKENが発行される(204)"() {
        given:
        def user = new AuthenticatedUser(3L, "j2ee", ["USER"])
        def refreshCookie = new Cookie(AuthCookieSupport.REFRESH_TOKEN_COOKIE, jwtService.generateRefreshToken(user))

        when:
        def result = mockMvc.perform(post("/api/auth/refresh").with(csrf()).cookie(refreshCookie))
                .andExpect(status().isNoContent())
                .andReturn()

        then:
        def accessCookieHeader = result.response.getHeaders("Set-Cookie").find { it.startsWith("ACCESS_TOKEN=") }
        accessCookieHeader != null
        def newToken = accessCookieHeader.split(";")[0].split("=", 2)[1]
        jwtService.parseToken(newToken).isPresent()
    }
}
