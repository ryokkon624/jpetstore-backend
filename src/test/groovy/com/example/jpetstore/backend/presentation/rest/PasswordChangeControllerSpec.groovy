package com.example.jpetstore.backend.presentation.rest

import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.infrastructure.security.AuthCookieSupport
import com.example.jpetstore.backend.infrastructure.security.JwtService
import com.example.jpetstore.backend.support.IntegrationTestBase
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import spock.lang.Tag

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * #15 AC1〜AC2・AC-neg1、#16 PW変更ぶんのCSRF回帰: パスワード変更（現在PW再認証必須・トークンローテート）の
 * end-to-end実証。
 *
 * <p>{@code m_signon}フィクスチャは{@code AuthLoginLogoutSpec}と同型でテスト自身が自己完結でINSERTする
 * （{@code jpetstore-backend/src/test/resources/flyway/sql/README.md}方針・既知bcryptハッシュ）。
 *
 * <p>三系統のステータス分離（計画フェーズ確定）を実証する: 現在PW誤り=422／弱PW・不正入力=400／
 * 真の未認証=401／CSRF欠落=403。{@code SecurityConfig}は無変更（{@code /api/account/**}は既存の{@code
 * anyRequest().authenticated()}配下）。
 */
@Tag("integration")
@AutoConfigureMockMvc
class PasswordChangeControllerSpec extends IntegrationTestBase {

    private static final String USERNAME = "pw_change_test_user"
    private static final String CURRENT_PASSWORD = "Correct#Passw0rd!"
    private static final String NEW_PASSWORD = "NewCorrect#Passw1!"

    @Autowired
    MockMvc mockMvc

    @Autowired
    JwtService jwtService

    @Autowired
    JdbcTemplate jdbcTemplate

    @Autowired
    PasswordEncoder passwordEncoder

    Long userId
    Cookie accessTokenCookie

    void setup() {
        cleanupFixtures()
        userId = insertTestUser()
        accessTokenCookie = new Cookie(
                AuthCookieSupport.ACCESS_TOKEN_COOKIE,
                jwtService.generateAccessToken(new AuthenticatedUser(userId, USERNAME, ["USER"])))
    }

    void cleanup() {
        cleanupFixtures()
    }

    private void cleanupFixtures() {
        jdbcTemplate.update(
                "DELETE FROM m_signon WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)", USERNAME)
        jdbcTemplate.update(
                "DELETE FROM m_profile WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)", USERNAME)
        jdbcTemplate.update("DELETE FROM m_account WHERE username = ?", USERNAME)
    }

    private Long insertTestUser() {
        jdbcTemplate.update(
                """
                INSERT INTO m_account
                    (username, email, first_name, last_name, status, address1, city, state, postal_code, country, phone,
                     create_program, update_program)
                VALUES (?, ?, 'PwChange', 'TestUser', 'OK', '1 Test St', 'Testville', 'CA', '90000', 'USA', '555-0100',
                     'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                USERNAME, "${USERNAME}@example.com")
        Long id = jdbcTemplate.queryForObject("SELECT user_id FROM m_account WHERE username = ?", Long, USERNAME)
        jdbcTemplate.update(
                """
                INSERT INTO m_signon (user_id, password_hash, create_program, update_program)
                VALUES (?, ?, 'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                id, passwordEncoder.encode(CURRENT_PASSWORD))
        id
    }

    private static String changeBody(String currentPassword, String newPassword) {
        """{"currentPassword":"${currentPassword}","newPassword":"${newPassword}"}"""
    }

    private String storedPasswordHash() {
        jdbcTemplate.queryForObject("SELECT password_hash FROM m_signon WHERE user_id = ?", String, userId)
    }

    private static String cookieValue(MvcResult result, String name) {
        def header = result.response.getHeaders("Set-Cookie").find { it.startsWith("${name}=") }
        header == null ? null : header.split(";")[0].split("=", 2)[1]
    }

    def "AC1/AC2: 正しい現在PWで変更すると204になりhashが更新され、新しいトークンが発行される(Q3)"() {
        given:
        def originalHash = storedPasswordHash()

        when:
        def result = mockMvc.perform(post("/api/account/password").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(changeBody(CURRENT_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn()

        then: "password_hashが更新され、新PWでのみ照合できる(旧PWは失効)"
        def newHash = storedPasswordHash()
        newHash != originalHash
        passwordEncoder.matches(NEW_PASSWORD, newHash)
        !passwordEncoder.matches(CURRENT_PASSWORD, newHash)

        and: "トークンローテート(Q3): 新しいACCESS_TOKENが発行される"
        def newAccessToken = cookieValue(result, AuthCookieSupport.ACCESS_TOKEN_COOKIE)
        newAccessToken != null
        jwtService.parseAccessToken(newAccessToken).map { it.username() }.orElse(null) == USERNAME
    }

    def "AC-neg1: 現在PWが誤りだと422になり、hashは変更されない"() {
        given:
        def originalHash = storedPasswordHash()

        expect:
        mockMvc.perform(post("/api/account/password").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(changeBody("WrongCurrent#1", NEW_PASSWORD)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath('$.code').value("UNPROCESSABLE_ENTITY"))

        and:
        storedPasswordHash() == originalHash
    }

    def "AC-neg1: 現在PWが空だと400になる(@NotBlank)"() {
        expect:
        mockMvc.perform(post("/api/account/password").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(changeBody("", NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
    }

    def "#17 Q1: 弱い新PW(文字種1種のみ)は400になり、hashは変更されない"() {
        given:
        def originalHash = storedPasswordHash()

        expect:
        mockMvc.perform(post("/api/account/password").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(changeBody(CURRENT_PASSWORD, "alllowercase")))
                .andExpect(status().isBadRequest())

        and:
        storedPasswordHash() == originalHash
    }

    def "未認証(Cookie無し)でPOST /api/account/passwordを叩くと401になる"() {
        expect:
        mockMvc.perform(post("/api/account/password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(changeBody(CURRENT_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
    }

    def "#16 AC-neg1(SBD-3): CSRFトークン無しでPOST /api/account/passwordすると403になり、hashは変更されない"() {
        given:
        def originalHash = storedPasswordHash()

        expect:
        mockMvc.perform(post("/api/account/password").cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(changeBody(CURRENT_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isForbidden())

        and:
        storedPasswordHash() == originalHash
    }

    def "#16 AC-neg1: GET /api/account/passwordは405になる(状態変更エンドポイントはPOSTのみ)"() {
        expect:
        mockMvc.perform(get("/api/account/password").cookie(accessTokenCookie))
                .andExpect(status().isMethodNotAllowed())
    }
}
