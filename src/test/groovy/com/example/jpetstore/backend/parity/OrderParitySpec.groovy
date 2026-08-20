package com.example.jpetstore.backend.parity

import com.example.jpetstore.backend.parity.canonical.ParityComparator
import com.example.jpetstore.backend.parity.canonical.ParityGolden
import com.example.jpetstore.backend.parity.canonical.ParityGoldenIO
import com.example.jpetstore.backend.parity.canonical.ParitySnapshot
import com.example.jpetstore.backend.parity.verify.NewDbReader
import com.example.jpetstore.backend.parity.verify.NewHttpClient
import com.example.jpetstore.backend.parity.verify.NewScenarioRunner
import com.example.jpetstore.backend.parity.verify.ParityIntegrationTestBase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import spock.lang.Tag

/**
 * W1（{@code order-single-item}）の新側verify（#48 AC9・AC12・dual-tag）。
 *
 * <p>コミット済みgolden（legacy採取済み）とのみ比較する。legacyの起動は不要（AC12）。
 * {@code USER_PRIMARY}（D3）は本specのフィクスチャで {@code m_account}/{@code m_signon} へ
 * {@code demo_user}/{@code Sprint3-DemoLogin!26} をINSERTして用意する（{@code R__test_user.sql}は同期しない）。
 */
@Tag("integration")
@Tag("parity")
class OrderParitySpec extends ParityIntegrationTestBase {

    private static final String USERNAME = "demo_user"
    private static final String PASSWORD = "Sprint3-DemoLogin!26"

    @Autowired
    JdbcTemplate jdbcTemplate

    @Autowired
    PasswordEncoder passwordEncoder

    NewHttpClient http
    long userId

    void setup() {
        cleanupFixtures()
        jdbcTemplate.update(
                """
                INSERT INTO m_account
                    (username, email, first_name, last_name, status, address1, city, state, postal_code, country, phone,
                     create_program, update_program)
                VALUES (?, 'demo_user@example.com', 'Demo', 'User', 'OK',
                     '901 San Antonio Road', 'Palo Alto', 'CA', '94303', 'USA', '555-0100',
                     'PARITY_FIXTURE', 'PARITY_FIXTURE')
                """,
                USERNAME)
        userId = jdbcTemplate.queryForObject("SELECT user_id FROM m_account WHERE username = ?", Long, USERNAME)
        jdbcTemplate.update(
                """
                INSERT INTO m_signon (user_id, password_hash, create_program, update_program)
                VALUES (?, ?, 'PARITY_FIXTURE', 'PARITY_FIXTURE')
                """,
                userId, passwordEncoder.encode(PASSWORD))

        http = new NewHttpClient(baseUrl())
        def loginResponse = http.login(USERNAME, PASSWORD)
        assert loginResponse.statusCode() == 200: "demo_userログインに失敗: ${loginResponse.statusCode()} ${loginResponse.body()}"
    }

    void cleanup() {
        cleanupFixtures()
    }

    private void cleanupFixtures() {
        jdbcTemplate.update(
                "DELETE FROM t_cart_item WHERE cart_id IN (SELECT cart_id FROM t_cart WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?))",
                USERNAME)
        jdbcTemplate.update(
                "DELETE FROM t_cart WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)", USERNAME)
        jdbcTemplate.update(
                "DELETE FROM t_order_line WHERE order_id IN (SELECT order_id FROM t_order WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?))",
                USERNAME)
        jdbcTemplate.update(
                "DELETE FROM t_audit_log WHERE actor_user_id IN (SELECT user_id FROM m_account WHERE username = ?)",
                USERNAME)
        jdbcTemplate.update(
                "DELETE FROM t_order WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)", USERNAME)
        jdbcTemplate.update("DELETE FROM t_login_attempt WHERE username = ?", USERNAME)
        jdbcTemplate.update(
                "DELETE FROM m_signon WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)", USERNAME)
        jdbcTemplate.update("DELETE FROM m_account WHERE username = ?", USERNAME)
    }

    def "W1(order-single-item): 新側がcommit済みgoldenとEQUIVALENTである(#48 AC9/AC12)"() {
        given:
        ParityGolden golden = ParityGoldenIO.readFromClasspath("order-single-item")
        NewDbReader db = new NewDbReader(jdbcTemplate)
        NewScenarioRunner runner = new NewScenarioRunner(http, db, userId)

        when:
        ParitySnapshot actual = runner.run("order-single-item")
        def result = ParityComparator.compare(
                golden.scenario, golden.expectation, golden.divergentFields, golden.snapshot, actual)

        then:
        result.pass
        // 失敗時はresult.messageにフィールド単位の差分(field=... golden(legacy)=... actual(new)=...)が出る(AC-neg1)
    }
}
