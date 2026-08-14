package com.example.jpetstore.backend.domain.concurrency

import com.example.jpetstore.backend.domain.exception.OptimisticLockConflictException
import com.example.jpetstore.backend.support.IntegrationTestBase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Tag

/**
 * AC8 (arch §4.2): version 楽観ロックの UPDATE 意味論を実DB(m_account)で実証する。
 * ドメインサービスは作らず、JdbcTemplate で直接 SQL の意味論のみを検証する
 * （実サービスは #14 account編集 送り）。
 */
@Tag("integration")
class OptimisticLockSqlSemanticsSpec extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbcTemplate

    Long userId

    void setup() {
        jdbcTemplate.update("""
            INSERT INTO m_account
                (username, email, first_name, last_name, status,
                 address1, city, state, postal_code, country, phone, version,
                 create_program, update_program)
            VALUES
                (?, 'v-test@example.com', 'Ver', 'Test', 'OK',
                 '1 Test St', 'Testville', 'CA', '90000', 'USA', '555-0100', 0,
                 'TEST', 'TEST')
            """, "version_test_user_" + UUID.randomUUID())
        userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM m_account WHERE email = 'v-test@example.com' ORDER BY user_id DESC LIMIT 1",
                Long)
    }

    void cleanup() {
        jdbcTemplate.update("DELETE FROM m_account WHERE user_id = ?", userId)
    }

    def "readVersion一致でUPDATEするとaffected rows=1・versionがインクリメントされる"() {
        when:
        int rows = jdbcTemplate.update(
                "UPDATE m_account SET first_name = ?, version = version + 1 WHERE user_id = ? AND version = ?",
                "Updated", userId, 0)

        then:
        rows == 1
        AffectedRows.requireUpdated(rows) // 例外を投げない
        jdbcTemplate.queryForObject("SELECT version FROM m_account WHERE user_id = ?", Integer, userId) == 1
    }

    def "readVersion不一致(他者が先に更新済み)だとaffected rows=0で競合として検出できる"() {
        given: "他リクエストが先にversionを1へ進めている"
        jdbcTemplate.update(
                "UPDATE m_account SET version = version + 1 WHERE user_id = ?", userId)

        when: "古いreadVersion(0)のままUPDATEを試みる"
        int rows = jdbcTemplate.update(
                "UPDATE m_account SET first_name = ?, version = version + 1 WHERE user_id = ? AND version = ?",
                "StaleUpdate", userId, 0)

        then:
        rows == 0

        when:
        AffectedRows.requireUpdated(rows)

        then:
        thrown(OptimisticLockConflictException)
        // 更新は反映されていないこと(before値のまま)
        jdbcTemplate.queryForObject("SELECT first_name FROM m_account WHERE user_id = ?", String, userId) != "StaleUpdate"
    }
}
