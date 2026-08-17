package com.example.jpetstore.backend.infrastructure.audit

import com.example.jpetstore.backend.support.IntegrationTestBase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import spock.lang.Tag

/**
 * AC7: 監査ログ記録機構（{@code t_audit_log} への書き込み）の実証。
 */
@Tag("integration")
class AuditLogRecorderSpec extends IntegrationTestBase {

    @Autowired
    AuditLogRecorder recorder

    @Autowired
    JdbcTemplate jdbcTemplate

    @Autowired
    PlatformTransactionManager transactionManager

    void setup() {
        jdbcTemplate.update("DELETE FROM t_audit_log")
    }

    def "recordAuthzFailureはevent_type=AUTHZ_FAILURE/result=DENIEDで1行記録する"() {
        given:
        def request = new MockHttpServletRequest()
        request.setRemoteAddr("203.0.113.10")

        when:
        recorder.recordAuthzFailure("SecuredPingController#ping", "insufficient role", request)

        then:
        def row = jdbcTemplate.queryForMap("SELECT * FROM t_audit_log ORDER BY audit_id DESC LIMIT 1")
        row.event_type == "AUTHZ_FAILURE"
        row.action == "SecuredPingController#ping"
        row.result == "DENIED"
        row.client_ip == "203.0.113.10"
        row.actor_user_id == null
        // WHO インターセプタが create_program/update_program を補完する(未設定ならSYSTEM)。
        row.create_program != null
        row.update_program != null
    }

    def "X-Forwarded-Forヘッダがあっても無条件に信頼せずgetRemoteAddr()を使う(レビュー指摘対応)"() {
        given: "信頼できないプロキシ設定を想定し、XFFヘッダを偽装したリクエスト"
        def request = new MockHttpServletRequest()
        request.setRemoteAddr("203.0.113.10")
        request.addHeader("X-Forwarded-For", "198.51.100.99")

        when:
        recorder.recordAuthzFailure("SecuredPingController#ping", "insufficient role", request)

        then:
        def row = jdbcTemplate.queryForMap("SELECT * FROM t_audit_log ORDER BY audit_id DESC LIMIT 1")
        row.client_ip == "203.0.113.10"
    }

    def "recordStateChangeは指定したevent_type=STATE_CHANGEで1行記録する"() {
        when:
        recorder.recordStateChange("ORDER_CREATE", "ORDER", "123", "SUCCESS", [orderId: 123, total: 45.6])

        then:
        def row = jdbcTemplate.queryForMap("SELECT * FROM t_audit_log ORDER BY audit_id DESC LIMIT 1")
        row.event_type == "STATE_CHANGE"
        row.action == "ORDER_CREATE"
        row.target_type == "ORDER"
        row.target_id == "123"
        row.result == "SUCCESS"
        row.detail.toString().contains("orderId")
    }

    def "detailがnullでも例外にならず記録できる"() {
        when:
        recorder.recordStateChange("EDIT_ACCOUNT", "ACCOUNT", "7", "SUCCESS", null)

        then:
        def row = jdbcTemplate.queryForMap("SELECT * FROM t_audit_log ORDER BY audit_id DESC LIMIT 1")
        row.detail == null
    }

    def "recordStateChangeIndependentlyは呼び出し元のトランザクションがロールバックしても記録が残る(REQUIRES_NEW・#8失敗監査)"() {
        given: "外側のトランザクションが後でロールバックされる状況を再現する"
        def outerTx = new TransactionTemplate(transactionManager)

        when:
        outerTx.execute { status ->
            recorder.recordStateChangeIndependently(
                    "ORDER_CREATE", null, null, "FAILURE", [reason: "INSUFFICIENT_STOCK", itemId: "EST-3"])
            status.setRollbackOnly()
            null
        }

        then: "REQUIRES_NEWの別トランザクションで既にコミット済みのため、外側のロールバックの影響を受けない"
        def row = jdbcTemplate.queryForMap("SELECT * FROM t_audit_log ORDER BY audit_id DESC LIMIT 1")
        row.event_type == "STATE_CHANGE"
        row.action == "ORDER_CREATE"
        row.target_type == null
        row.target_id == null
        row.result == "FAILURE"
        row.detail.toString().contains("INSUFFICIENT_STOCK")
    }
}
