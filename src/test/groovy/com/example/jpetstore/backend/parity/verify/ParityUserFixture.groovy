package com.example.jpetstore.backend.parity.verify

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * 新側{@code demo_user}フィクスチャの投入・後始末を1箇所に集約する（#51 T1・design.md AC-neg5）。
 * {@code OrderParitySpec}/{@code OrderHistoryParitySpec}/{@code CartParitySpec}が共用する
 * （既存{@code OrderParitySpec}のsetup/cleanupFixturesをそのまま抽出した最小retrofit）。
 *
 * <p>{@code m_profile}も必ずINSERTする（{@code AccountEditCustomMapper.findByUserId}が
 * {@code m_account JOIN m_profile}のため。{@code R__}はTestcontainers経路に届かないためテスト側で
 * 直接INSERTする＝AC-neg5・D3の教訓の再発防止）。
 */
class ParityUserFixture {

    static final String USERNAME = "demo_user"
    static final String PASSWORD = "Sprint3-DemoLogin!26"

    private final JdbcTemplate jdbcTemplate
    private final PasswordEncoder passwordEncoder

    ParityUserFixture(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate
        this.passwordEncoder = passwordEncoder
    }

    /** {@code demo_user}の{@code m_account}/{@code m_signon}/{@code m_profile}を投入し、user_idを返す。 */
    long setUp() {
        cleanUp()
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
        long userId = jdbcTemplate.queryForObject("SELECT user_id FROM m_account WHERE username = ?", Long, USERNAME)
        jdbcTemplate.update(
                """
                INSERT INTO m_signon (user_id, password_hash, create_program, update_program)
                VALUES (?, ?, 'PARITY_FIXTURE', 'PARITY_FIXTURE')
                """,
                userId, passwordEncoder.encode(PASSWORD))
        jdbcTemplate.update(
                """
                INSERT INTO m_profile
                    (user_id, language_preference, favorite_category_id, create_program, update_program)
                VALUES (?, 'english', 'FISH', 'PARITY_FIXTURE', 'PARITY_FIXTURE')
                """,
                userId)
        return userId
    }

    /** {@code demo_user}に紐づく行(cart/order/audit/login_attempt/profile/signon/account)を全て削除する。 */
    void cleanUp() {
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
                "DELETE FROM m_profile WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)", USERNAME)
        jdbcTemplate.update(
                "DELETE FROM m_signon WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)", USERNAME)
        jdbcTemplate.update("DELETE FROM m_account WHERE username = ?", USERNAME)
    }
}
