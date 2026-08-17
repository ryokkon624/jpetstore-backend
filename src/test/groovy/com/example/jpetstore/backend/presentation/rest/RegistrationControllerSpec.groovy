package com.example.jpetstore.backend.presentation.rest

import com.example.jpetstore.backend.infrastructure.security.AuthCookieSupport
import com.example.jpetstore.backend.infrastructure.security.JwtService
import com.example.jpetstore.backend.infrastructure.security.RegisterAttemptProperties
import com.example.jpetstore.backend.support.IntegrationTestBase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.RequestPostProcessor
import spock.lang.Tag

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * #13 AC1〜AC6・[L2]・AC-neg1/AC-neg2: ユーザー登録（自動ログイン・DB-backedレート制限）end-to-end実証。
 *
 * <p>各テストは専用のclient_ip（{@code request.setRemoteAddr}）を使い、レート制限テーブル
 * （{@code t_register_attempt}）の状態がテスト間で汚染されないよう隔離する（{@code
 * OrderConcurrencyIntegrationSpec}のテスト専用ID命名の教訓を踏襲）。
 */
@Tag("integration")
@AutoConfigureMockMvc
class RegistrationControllerSpec extends IntegrationTestBase {

    @Autowired
    MockMvc mockMvc

    @Autowired
    JwtService jwtService

    @Autowired
    JdbcTemplate jdbcTemplate

    @Autowired
    RegisterAttemptProperties registerAttemptProperties

    void setup() {
        jdbcTemplate.update("DELETE FROM t_register_attempt")
        jdbcTemplate.update("DELETE FROM m_profile WHERE user_id IN (SELECT user_id FROM m_account WHERE username LIKE 'register_test_%')")
        jdbcTemplate.update("DELETE FROM m_signon WHERE user_id IN (SELECT user_id FROM m_account WHERE username LIKE 'register_test_%')")
        jdbcTemplate.update("DELETE FROM m_account WHERE username LIKE 'register_test_%'")
    }

    void cleanup() {
        jdbcTemplate.update("DELETE FROM t_register_attempt")
        jdbcTemplate.update("DELETE FROM m_profile WHERE user_id IN (SELECT user_id FROM m_account WHERE username LIKE 'register_test_%')")
        jdbcTemplate.update("DELETE FROM m_signon WHERE user_id IN (SELECT user_id FROM m_account WHERE username LIKE 'register_test_%')")
        jdbcTemplate.update("DELETE FROM m_account WHERE username LIKE 'register_test_%'")
    }

    private static String registerBody(String username, String password = "Correct#Passw0rd!",
                                        String repeatedPassword = password) {
        """
        {
          "username": "${username}",
          "password": "${password}",
          "repeatedPassword": "${repeatedPassword}",
          "email": "${username}@example.com",
          "firstName": "Taro",
          "lastName": "Yamada",
          "address1": "1 Test St",
          "address2": "Suite 2",
          "city": "Testville",
          "state": "CA",
          "postalCode": "90000",
          "country": "USA",
          "phone": "555-0100"
        }
        """
    }

    private static RequestPostProcessor remoteAddr(String clientIp) {
        { MockHttpServletRequest req -> req.setRemoteAddr(clientIp); req } as RequestPostProcessor
    }

    private ResultActions attemptRegister(String body, String clientIp) {
        mockMvc.perform(post("/api/register")
                .with(csrf())
                .with(remoteAddr(clientIp))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
    }

    private static String cookieValue(MvcResult result, String name) {
        def header = result.response.getHeaders("Set-Cookie").find { it.startsWith("${name}=") }
        header == null ? null : header.split(";")[0].split("=", 2)[1]
    }

    def "AC1/AC2: 正しい入力で登録すると201で自動ログインされACCESS_TOKEN/REFRESH_TOKENが発行される"() {
        given:
        def username = "register_test_success"

        when:
        def result = attemptRegister(registerBody(username), "198.51.100.10")
                .andExpect(status().isCreated())
                .andExpect(jsonPath('$.username').value(username))
                .andReturn()

        then:
        def accessToken = cookieValue(result, AuthCookieSupport.ACCESS_TOKEN_COOKIE)
        def refreshToken = cookieValue(result, AuthCookieSupport.REFRESH_TOKEN_COOKIE)
        accessToken != null
        refreshToken != null
        jwtService.parseAccessToken(accessToken).map { it.username() }.orElse(null) == username

        and: "account/signon/profileが各1行、入力値どおり作成される([L2]・AC1)"
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM m_account WHERE username = ?", Integer, username) == 1
        jdbcTemplate.queryForObject("SELECT email FROM m_account WHERE username = ?", String, username) == "${username}@example.com"
        jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM m_signon s JOIN m_account a ON a.user_id = s.user_id WHERE a.username = ?",
                Integer, username) == 1
        jdbcTemplate.queryForObject(
                "SELECT language_preference FROM m_profile p JOIN m_account a ON a.user_id = p.user_id WHERE a.username = ?",
                String, username) == "english"
    }

    def "AC-neg1(SBD-5): 登録後のpassword_hashはbcryptプレフィックス付きで平文と一致しない"() {
        given:
        def username = "register_test_bcrypt"

        when:
        attemptRegister(registerBody(username, "PlainTextPassw0rd!"), "198.51.100.11")
                .andExpect(status().isCreated())

        then:
        def hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM m_signon s JOIN m_account a ON a.user_id = s.user_id WHERE a.username = ?",
                String, username)
        hash.startsWith("{bcrypt}")
        hash != "PlainTextPassw0rd!"
    }

    def "自動ログインで発行されたACCESS_TOKENで保護resourceにアクセスできる(セッション再生成・AC2/SBD-4)"() {
        given:
        def username = "register_test_autologin"

        when:
        def result = attemptRegister(registerBody(username), "198.51.100.12")
                .andExpect(status().isCreated())
                .andReturn()
        def accessCookie = new jakarta.servlet.http.Cookie(
                AuthCookieSupport.ACCESS_TOKEN_COOKIE, cookieValue(result, AuthCookieSupport.ACCESS_TOKEN_COOKIE))

        then:
        mockMvc.perform(get("/api/auth/me").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.username').value(username))
    }

    def "パスワード不一致は400になりアカウントは作成されない"() {
        given:
        def username = "register_test_mismatch"

        when:
        attemptRegister(registerBody(username, "PasswordA1!", "PasswordB1!"), "198.51.100.13")
                .andExpect(status().isBadRequest())

        then:
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM m_account WHERE username = ?", Integer, username) == 0
    }

    def "E4: username重複は409になり明示メッセージを返す(既存アカウントは変更されない)"() {
        given:
        def username = "register_test_dup"
        attemptRegister(registerBody(username), "198.51.100.14").andExpect(status().isCreated())

        expect:
        attemptRegister(registerBody(username), "198.51.100.14")
                .andExpect(status().isConflict())
                .andExpect(jsonPath('$.code').value("CONFLICT"))

        and: "重複行は作られない(1行のまま)"
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM m_account WHERE username = ?", Integer, username) == 1
    }

    def "必須項目が空だと400になる(AC1・バリデーション)"() {
        expect:
        mockMvc.perform(post("/api/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"username":"","password":"","repeatedPassword":""}'))
                .andExpect(status().isBadRequest())
    }

    def "AC-neg2(SBD-6): 同一IPからの登録試行が閾値を超えると429になる(列挙/総当り対策)"() {
        given:
        def clientIp = "198.51.100.20"
        def mismatchBody = registerBody("register_test_ratelimit", "PasswordA1!", "PasswordB1!")

        registerAttemptProperties.maxAttempts().times {
            attemptRegister(mismatchBody, clientIp).andExpect(status().isBadRequest())
        }

        expect: "閾値超過後は有効な入力でも429になる(登録試行自体を抑止)"
        attemptRegister(registerBody("register_test_ratelimit_ok"), clientIp)
                .andExpect(status().isTooManyRequests())

        and: "429によりアカウントは作成されない"
        jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM m_account WHERE username = ?", Integer, "register_test_ratelimit_ok") == 0
    }

    def "別IPからの登録試行はレート制限の影響を受けない(client_ip単位で分離)"() {
        given:
        def lockedIp = "198.51.100.21"
        def otherIp = "198.51.100.22"
        def mismatchBody = registerBody("register_test_isolation", "PasswordA1!", "PasswordB1!")
        registerAttemptProperties.maxAttempts().times {
            attemptRegister(mismatchBody, lockedIp).andExpect(status().isBadRequest())
        }

        expect:
        attemptRegister(registerBody("register_test_isolation_ok"), otherIp)
                .andExpect(status().isCreated())
    }

    def "CSRFトークン無しでPOST /api/registerすると403になる(SBD-3)"() {
        expect:
        mockMvc.perform(post("/api/register")
                .with(remoteAddr("198.51.100.30"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("register_test_csrf")))
                .andExpect(status().isForbidden())
    }

    def "GET /api/registerは405になる(状態変更エンドポイントはPOSTのみ)"() {
        expect:
        mockMvc.perform(get("/api/register"))
                .andExpect(status().isMethodNotAllowed())
    }
}
