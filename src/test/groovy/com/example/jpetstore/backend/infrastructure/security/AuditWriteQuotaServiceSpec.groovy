package com.example.jpetstore.backend.infrastructure.security

import com.example.jpetstore.backend.support.IntegrationTestBase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Tag

/**
 * #39 AC3/AC-neg3(N14): 未認証由来の監査write抑止ゲート（{@link AuditWriteQuotaService}）の窓内上限を実DBで実証する。
 */
@Tag("integration")
class AuditWriteQuotaServiceSpec extends IntegrationTestBase {

    private static final String CLIENT_IP = "198.51.100.42"

    @Autowired
    AuditWriteQuotaService service

    @Autowired
    AuditWriteQuotaProperties properties

    @Autowired
    JdbcTemplate jdbcTemplate

    void setup() {
        jdbcTemplate.update("DELETE FROM t_audit_write_quota")
    }

    def "窓内上限(maxWrites)までは枠確保に成功する"() {
        expect:
        (1..properties.maxWrites()).every { service.tryAcquire(CLIENT_IP) }
    }

    def "窓内上限を超えると枠確保に失敗しsuppressed_countが増える(AC-neg3・黙って消えない)"() {
        given:
        properties.maxWrites().times { service.tryAcquire(CLIENT_IP) }

        when:
        def acquired = service.tryAcquire(CLIENT_IP)

        then: "頭打ちになり、これ以上authenticate相当のwriteには進めない"
        !acquired

        and: "抑止の事実がsuppressed_countへ記録される(黙って消えないことの証跡)"
        def row = jdbcTemplate.queryForMap(
                "SELECT * FROM t_audit_write_quota WHERE client_ip = ?", CLIENT_IP)
        row.write_count == properties.maxWrites()
        row.suppressed_count == 1
    }

    def "上限超過後さらに呼んでもwrite_countは上限を大きく超過しない(頭打ち)"() {
        given:
        (properties.maxWrites() + 5).times { service.tryAcquire(CLIENT_IP) }

        expect:
        def row = jdbcTemplate.queryForMap(
                "SELECT * FROM t_audit_write_quota WHERE client_ip = ?", CLIENT_IP)
        row.write_count == properties.maxWrites()
        row.suppressed_count == 5
    }

    def "別のclient_ipは独立してカウントされる"() {
        given:
        def otherIp = "198.51.100.43"
        properties.maxWrites().times { service.tryAcquire(CLIENT_IP) }

        expect:
        service.tryAcquire(otherIp)
    }
}
