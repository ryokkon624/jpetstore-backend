package com.example.jpetstore.backend.presentation.rest

import com.example.jpetstore.backend.infrastructure.security.LoginAttemptProperties
import com.example.jpetstore.backend.support.IntegrationTestBase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import spock.lang.Tag

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * #20 AC1/AC3/AC-neg1（SBD-6）end-to-end 実証。
 *
 * <p>{@code t_login_attempt}（Draft 1・分離表）による username 単位のレート制限/ロックアウトを、実際の
 * {@code POST /api/auth/login} 経由で検証する。ロック中の応答は通常の誤資格401と区別できないこと（列挙不可）、
 * 未知 username も既知 username と対称にロック対象になること（password spray対策の非対称性が無いこと）、
 * ロック解除・成功時のカウンタリセットも検証する。
 */
@Tag("integration")
@AutoConfigureMockMvc
class LoginLockoutSpec extends IntegrationTestBase {

    private static final String USERNAME = "lockout_test_user"
    private static final String RAW_PASSWORD = "Correct#Passw0rd!"
    private static final String WRONG_PASSWORD = "wrong-password"

    @Autowired
    MockMvc mockMvc

    @Autowired
    JdbcTemplate jdbcTemplate

    @Autowired
    PasswordEncoder passwordEncoder

    @Autowired
    LoginAttemptProperties loginAttemptProperties

    void setup() {
        jdbcTemplate.update("DELETE FROM t_login_attempt")
        jdbcTemplate.update("DELETE FROM t_audit_log")
        jdbcTemplate.update("DELETE FROM m_signon WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)", USERNAME)
        jdbcTemplate.update("DELETE FROM m_account WHERE username = ?", USERNAME)
        insertLockoutTestUser()
    }

    private void insertLockoutTestUser() {
        jdbcTemplate.update(
                """
                INSERT INTO m_account
                    (username, email, first_name, last_name, status, address1, city, state, postal_code, country, phone,
                     create_program, update_program)
                VALUES (?, ?, 'Lockout', 'TestUser', 'OK', '1 Test St', 'Testville', 'CA', '90000', 'USA', '555-0100',
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

    private ResultActions attemptLogin(String username, String password) {
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(username, password)))
    }

    private static String normalizeTimestamp(String json) {
        json.replaceAll(/"timestamp":"[^"]*"/, '"timestamp":"X"')
    }

    private int maxAttempts() {
        loginAttemptProperties.maxAttempts()
    }

    def "MAX_ATTEMPTS回連続失敗すると、その後は正しいpasswordでもログインできなくなる(AC1)"() {
        given:
        maxAttempts().times { attemptLogin(USERNAME, WRONG_PASSWORD).andExpect(status().isUnauthorized()) }

        expect: "ロック中は正しいpasswordでも401"
        attemptLogin(USERNAME, RAW_PASSWORD).andExpect(status().isUnauthorized())
    }

    def "ロック時の応答は通常の誤資格401と完全に同一(timestamp以外)(AC3・列挙不可)"() {
        given:
        maxAttempts().times { attemptLogin(USERNAME, WRONG_PASSWORD) }

        when:
        def lockedResult = attemptLogin(USERNAME, WRONG_PASSWORD).andReturn()
        def normalWrongResult = attemptLogin("never_locked_${System.nanoTime()}", WRONG_PASSWORD).andReturn()

        then:
        lockedResult.response.status == 401
        normalWrongResult.response.status == 401
        normalizeTimestamp(lockedResult.response.contentAsString) ==
                normalizeTimestamp(normalWrongResult.response.contentAsString)
    }

    def "存在しないusernameもMAX_ATTEMPTS回失敗するとロックされる(SBD-6・password spray対策の対称性)"() {
        given:
        def unknown = "no_such_user_${System.nanoTime()}"
        maxAttempts().times { attemptLogin(unknown, "whatever").andExpect(status().isUnauthorized()) }

        expect:
        attemptLogin(unknown, "whatever").andExpect(status().isUnauthorized())

        and: "DBには実在しないusername宛の行が作られる(FK無しの設計どおり)"
        jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_login_attempt WHERE username = ?", Integer, unknown) == 1
    }

    def "lock_untilを過去に書き換える(ロック期間経過)と、正しいpasswordで再度ログインできる"() {
        given: "DB自身の時計基準で過去に書き換える(app/DB間のクロックスキューに影響されないようDB側で計算する)"
        maxAttempts().times { attemptLogin(USERNAME, WRONG_PASSWORD) }
        jdbcTemplate.update(
                "UPDATE t_login_attempt SET lock_until = DATE_SUB(NOW(6), INTERVAL 1 MINUTE) WHERE username = ?",
                USERNAME)

        expect:
        attemptLogin(USERNAME, RAW_PASSWORD).andExpect(status().isOk())
    }

    def "ログイン成功でカウンタがリセットされ、成功後はMAX_ATTEMPTS回失敗するまでロックされない(recordSuccess)"() {
        given: "閾値未満の失敗のあとログイン成功"
        (maxAttempts() - 1).times { attemptLogin(USERNAME, WRONG_PASSWORD) }
        attemptLogin(USERNAME, RAW_PASSWORD).andExpect(status().isOk())

        when: "成功後、再度MAX_ATTEMPTS-1回失敗してもまだロックされない"
        (maxAttempts() - 1).times { attemptLogin(USERNAME, WRONG_PASSWORD) }

        then:
        attemptLogin(USERNAME, RAW_PASSWORD).andExpect(status().isOk())
    }
}
