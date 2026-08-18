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
 * ログイン/ログアウト（#18）の end-to-end 実証。
 *
 * <p>#19 で用意した {@code PasswordEncoder} を使い、テスト自身が {@code m_account}/{@code m_signon}
 * にフィクスチャを自己完結で INSERT する（{@code jpetstore-backend/src/test/resources/flyway/sql/README.md}
 * の方針: {@code flyway/sql-test} は backend の統合テストへ同期せず、各テストが自己完結でフィクスチャを用意する）。
 */
@Tag("integration")
@AutoConfigureMockMvc
class AuthLoginLogoutSpec extends IntegrationTestBase {

    private static final String USERNAME = "login_test_user"
    private static final String RAW_PASSWORD = "Correct#Passw0rd!"

    @Autowired
    MockMvc mockMvc

    @Autowired
    JwtService jwtService

    @Autowired
    JdbcTemplate jdbcTemplate

    @Autowired
    PasswordEncoder passwordEncoder

    void setup() {
        jdbcTemplate.update("DELETE FROM t_audit_log")
        // #20 AC1(SBD-6): t_login_attempt はusername単位で失敗カウンタを永続化するため、テストごとに
        // クリアしないと本Specの複数メソッドが同一USERNAMEへの誤PW試行を積み重ねてしまい、
        // ロック閾値到達によって後続テストの「正しいpasswordで200」が意図せず401化する(テスト間の状態汚染)。
        jdbcTemplate.update("DELETE FROM t_login_attempt")
        jdbcTemplate.update("DELETE FROM m_signon WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)", USERNAME)
        // #36/#25: 一部テストがm_profileへ直接INSERTするため、m_account削除前(FK ON DELETE RESTRICT)に
        // 必ず先に掃除する(次テストへの汚染防止・fk_m_profile_user_id違反回避)。
        jdbcTemplate.update("DELETE FROM m_profile WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)", USERNAME)
        jdbcTemplate.update("DELETE FROM m_account WHERE username = ?", USERNAME)
        insertLoginTestUser()
    }

    private void insertLoginTestUser() {
        jdbcTemplate.update(
                """
                INSERT INTO m_account
                    (username, email, first_name, last_name, status, address1, city, state, postal_code, country, phone,
                     create_program, update_program)
                VALUES (?, ?, 'Login', 'TestUser', 'OK', '1 Test St', 'Testville', 'CA', '90000', 'USA', '555-0100',
                     'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                USERNAME, "${USERNAME}@example.com")
        Long userId = jdbcTemplate.queryForObject("SELECT user_id FROM m_account WHERE username = ?", Long, USERNAME)
        jdbcTemplate.update(
                """
                INSERT INTO m_signon (user_id, password_hash, create_program, update_program)
                VALUES (?, ?, 'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                userId, passwordEncoder.encode(RAW_PASSWORD))
    }

    private static String loginBody(String username, String password) {
        """{"username":"${username}","password":"${password}"}"""
    }

    private static String cookieValue(MvcResult result, String name) {
        def header = result.response.getHeaders("Set-Cookie").find { it.startsWith("${name}=") }
        header == null ? null : header.split(";")[0].split("=", 2)[1]
    }

    def "正しいusername/passwordでログインすると200を返しACCESS_TOKEN/REFRESH_TOKENが発行される(AC1/AC2)"() {
        when:
        def result = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(USERNAME, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.username').value(USERNAME))
                // #36/#25: このユーザーはm_profile行を持たないフィクスチャのため、
                // AccountApplicationService#getPreferencesの既定値フォールバック(system/english)を確認する。
                .andExpect(jsonPath('$.colorSchemePreference').value("system"))
                .andExpect(jsonPath('$.languagePreference').value("english"))
                .andReturn()

        then:
        def accessToken = cookieValue(result, AuthCookieSupport.ACCESS_TOKEN_COOKIE)
        def refreshToken = cookieValue(result, AuthCookieSupport.REFRESH_TOKEN_COOKIE)
        accessToken != null
        refreshToken != null
        jwtService.parseAccessToken(accessToken).map { it.username() }.orElse(null) == USERNAME
        jwtService.parseRefreshToken(refreshToken).map { it.username() }.orElse(null) == USERNAME
    }

    def "AC6(#36)/AC7(#25): m_profileにDB保存済みのテーマ/言語設定があればログイン応答へそのまま含まれる(A2・login()実行中のcurrentUserProvider不使用)"() {
        given: "m_profileへdark/japaneseを保存しておく"
        Long userId = jdbcTemplate.queryForObject("SELECT user_id FROM m_account WHERE username = ?", Long, USERNAME)
        jdbcTemplate.update(
                """
                INSERT INTO m_profile (user_id, language_preference, favorite_category_id, color_scheme_preference, create_program, update_program)
                VALUES (?, 'japanese', NULL, 'dark', 'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                userId)

        expect:
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(USERNAME, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.colorSchemePreference').value("dark"))
                .andExpect(jsonPath('$.languagePreference').value("japanese"))
    }

    def "誤ったpasswordでログインすると401になる"() {
        expect:
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(USERNAME, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath('$.code').value("UNAUTHORIZED"))
    }

    def "未知のusernameでログインすると誤PWと同一の401になる(SBD-6・列挙不可)"() {
        when:
        def unknownResult = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("no_such_user", "whatever-password")))
                .andReturn()
        def wrongPasswordResult = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(USERNAME, "wrong-password")))
                .andReturn()

        then:
        unknownResult.response.status == 401
        wrongPasswordResult.response.status == 401

        and: "timestampを除き、応答内容(code/message/path)が完全一致する(列挙不可)"
        normalizeTimestamp(unknownResult.response.contentAsString) ==
                normalizeTimestamp(wrongPasswordResult.response.contentAsString)
    }

    private static String normalizeTimestamp(String json) {
        json.replaceAll(/"timestamp":"[^"]*"/, '"timestamp":"X"')
    }

    def "j2ee/j2eeでログインすると401になる(#19 AC-neg1・弱資格情報は存在しない)"() {
        expect:
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("j2ee", "j2ee")))
                .andExpect(status().isUnauthorized())
    }

    def "CSRFトークン無しでPOST /api/auth/loginすると403になる(AC4)"() {
        expect:
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(USERNAME, RAW_PASSWORD)))
                .andExpect(status().isForbidden())
    }

    def "CSRFトークン無しでPOST /api/auth/logoutすると403になる(AC4)"() {
        expect:
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isForbidden())
    }

    def "GET /api/auth/loginは405になる(AC-neg2・GET状態変更なし)"() {
        expect:
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
    }

    def "GET /api/auth/logoutは405になる(AC-neg2・GET状態変更なし)"() {
        expect:
        mockMvc.perform(get("/api/auth/logout"))
                .andExpect(status().isMethodNotAllowed())
    }

    def "ログイン成功時に発行されるACCESS_TOKENは新規トークンであり、事前に付与されたCookieは再利用されない(AC2/AC-neg1・SBD-4固定化対策)"() {
        given: "攻撃者が事前に細工した(別ユーザの)ACCESS_TOKEN Cookieをログイン前に持っている状態を模す"
        def attacker = new AuthenticatedUser(999999L, "attacker", ["USER"])
        def plantedToken = jwtService.generateAccessToken(attacker)
        def plantedCookie = new Cookie(AuthCookieSupport.ACCESS_TOKEN_COOKIE, plantedToken)

        when:
        def result = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .cookie(plantedCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(USERNAME, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()

        then:
        def newAccessToken = cookieValue(result, AuthCookieSupport.ACCESS_TOKEN_COOKIE)
        newAccessToken != null
        newAccessToken != plantedToken
        def newUser = jwtService.parseAccessToken(newAccessToken).orElseThrow()
        newUser.username() == USERNAME
        newUser.userId() != attacker.userId()
    }

    def "logoutはCSRF付きで204を返しACCESS_TOKEN/REFRESH_TOKENを失効させる(Max-Age=0)(AC2)"() {
        expect:
        def result = mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isNoContent())
                .andReturn()

        def headers = result.response.getHeaders("Set-Cookie")
        headers.any { it.startsWith("${AuthCookieSupport.ACCESS_TOKEN_COOKIE}=") && it.contains("Max-Age=0") }
        headers.any { it.startsWith("${AuthCookieSupport.REFRESH_TOKEN_COOKIE}=") && it.contains("Max-Age=0") }
    }

    def "forwardActionパラメータを付与してもリダイレクトされない(#20 AC2/AC-neg2・SBD-9・sink不在の回帰)"() {
        when:
        def result = mockMvc.perform(post("/api/auth/login?forwardAction=//evil.com")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(USERNAME, RAW_PASSWORD)))
                .andReturn()

        then: "レガシーのforwardAction相当のパラメータを付与しても、Locationヘッダも3xxも返らない(sink不在)"
        result.response.getHeader("Location") == null
        !(result.response.status in 300..399)
    }

    def "ログイン→保護resourceへのアクセス(role不足で403)→logout後は401になる、の一連が疎通する(AC1)"() {
        given: "未ログイン状態で保護resourceを叩くと401"
        mockMvc.perform(get("/api/secured/ping")).andExpect(status().isUnauthorized())

        when: "ログイン成功"
        def loginResult = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(USERNAME, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
        def accessCookie = new Cookie(AuthCookieSupport.ACCESS_TOKEN_COOKIE, cookieValue(loginResult, AuthCookieSupport.ACCESS_TOKEN_COOKIE))

        then: "発行されたACCESS_TOKENで保護resourceを叩くと、認証は通るがUSERロールのためADMIN限定resourceには403(認証チェーンが機能している証跡)"
        mockMvc.perform(get("/api/secured/ping").cookie(accessCookie)).andExpect(status().isForbidden())

        when: "logout"
        mockMvc.perform(post("/api/auth/logout").with(csrf()).cookie(accessCookie))
                .andExpect(status().isNoContent())

        then: "Cookie無しで保護resourceを叩くと401に戻る"
        mockMvc.perform(get("/api/secured/ping")).andExpect(status().isUnauthorized())
    }
}
