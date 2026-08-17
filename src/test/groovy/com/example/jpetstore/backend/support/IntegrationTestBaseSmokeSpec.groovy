package com.example.jpetstore.backend.support

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Tag

/**
 * {@link IntegrationTestBase} 自体の疎通確認（Testcontainers MySQL 起動 + Flyway 自動適用）。
 */
@Tag("integration")
class IntegrationTestBaseSmokeSpec extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbcTemplate

    def "Flywayスキーマが適用され業務テーブル(m_account)が存在する"() {
        expect:
        jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                Integer, DB_NAME, "m_account") == 1
    }

    def "flyway_schema_historyに全マイグレーション(11件)が成功として記録されている"() {
        // #1でV00_000_008(カタログseed)/V00_000_009(在庫ステータスm_code)を追加(7→9)。
        // #4でV00_000_010(カートテーブル)を追加(9→10)。
        // #9でV00_000_011(t_orderの(user_id, order_id)複合索引)を追加(10→11)。
        expect:
        jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                Integer) == 11
    }
}
