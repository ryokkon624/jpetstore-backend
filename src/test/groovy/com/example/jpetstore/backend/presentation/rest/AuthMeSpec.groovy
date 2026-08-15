package com.example.jpetstore.backend.presentation.rest

import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.infrastructure.security.AuthCookieSupport
import com.example.jpetstore.backend.infrastructure.security.JwtService
import com.example.jpetstore.backend.support.IntegrationTestBase
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Tag

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 認証済プリンシパルの再水和エンドポイント {@code GET /api/auth/me}（#24 論点①）。
 *
 * <p>httpOnly Cookie はリロード後もブラウザが自動送信するが、フロントの Pinia store（{@code username}/{@code
 * roles}）はリロードで揮発する。本エンドポイントで現在の認証プリンシパルを返し、フロントが起動時に identity を
 * 再水和できるようにする。
 *
 * <p>GET は冪等のため CSRF 不要。未認証は {@code SecurityConfig} の {@code anyRequest().authenticated()}
 * 配下（permitAll に入れない）のため、コントローラに到達する前に {@code AuditingAuthenticationEntryPoint} が
 * 401 を返す。
 */
@Tag("integration")
@AutoConfigureMockMvc
class AuthMeSpec extends IntegrationTestBase {

    @Autowired
    MockMvc mockMvc

    @Autowired
    JwtService jwtService

    private Cookie accessTokenCookie(AuthenticatedUser user) {
        new Cookie(AuthCookieSupport.ACCESS_TOKEN_COOKIE, jwtService.generateAccessToken(user))
    }

    def "認証済みでGET /api/auth/meを叩くと200で現在のusername/rolesを返す"() {
        given:
        def user = new AuthenticatedUser(7L, "me_test_user", ["USER", "ADMIN"])

        expect:
        mockMvc.perform(get("/api/auth/me").cookie(accessTokenCookie(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.username').value("me_test_user"))
                .andExpect(jsonPath('$.roles').isArray())
                .andExpect(jsonPath('$.roles[0]').value("USER"))
                .andExpect(jsonPath('$.roles[1]').value("ADMIN"))
    }

    def "未認証(Cookie無し)でGET /api/auth/meを叩くと401になる"() {
        expect:
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath('$.code').value("UNAUTHORIZED"))
    }

    def "CSRFトークン無しでもGET /api/auth/meは403にならない(GET=冪等・CSRF不要)"() {
        given:
        def user = new AuthenticatedUser(8L, "me_csrf_free_user", ["USER"])

        expect:
        mockMvc.perform(get("/api/auth/me").cookie(accessTokenCookie(user)))
                .andExpect(status().isOk())
    }
}
