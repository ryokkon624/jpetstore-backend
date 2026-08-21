package com.example.jpetstore.backend.parity

import com.example.jpetstore.backend.parity.canonical.ParityComparator
import com.example.jpetstore.backend.parity.canonical.ParityGolden
import com.example.jpetstore.backend.parity.canonical.ParityGoldenIO
import com.example.jpetstore.backend.parity.canonical.ParitySnapshot
import com.example.jpetstore.backend.parity.verify.NewDbReader
import com.example.jpetstore.backend.parity.verify.NewHttpClient
import com.example.jpetstore.backend.parity.verify.NewScenarioRunner
import com.example.jpetstore.backend.parity.verify.ParityIntegrationTestBase
import com.example.jpetstore.backend.parity.verify.ParityUserFixture
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import spock.lang.Tag
import spock.lang.Unroll

/**
 * 注文履歴照会（R7/R8a/R8b）の新側verify（#51 AC3・dual-tag）。
 *
 * <p>{@code demo_user}フィクスチャは{@link ParityUserFixture}を{@code OrderParitySpec}と共用する（最小retrofit）。
 * R7={@code orders-list}(EQUIVALENT)・R8a={@code order-detail-own}
 * (INTENDED_DIVERGENCE(ID-24)・Q6でEQUIVALENTから変更)・R8b={@code order-detail-missing}
 * (INTENDED_DIVERGENCE(ID-14)・Q1で新側は403に確定)。
 */
@Tag("integration")
@Tag("parity")
class OrderHistoryParitySpec extends ParityIntegrationTestBase {

    @Autowired
    JdbcTemplate jdbcTemplate

    @Autowired
    PasswordEncoder passwordEncoder

    ParityUserFixture fixture
    NewHttpClient http
    long userId

    void setup() {
        fixture = new ParityUserFixture(jdbcTemplate, passwordEncoder)
        userId = fixture.setUp()

        http = new NewHttpClient(baseUrl())
        def loginResponse = http.login(ParityUserFixture.USERNAME, ParityUserFixture.PASSWORD)
        assert loginResponse.statusCode() == 200: "demo_userログインに失敗: ${loginResponse.statusCode()} ${loginResponse.body()}"
    }

    void cleanup() {
        fixture.cleanUp()
    }

    @Unroll
    def "#scenarioId: 新側がcommit済みgoldenと宣言どおりの結果になる(#51 AC3/AC7)"() {
        given:
        ParityGolden golden = ParityGoldenIO.readFromClasspath(scenarioId)
        NewDbReader db = new NewDbReader(jdbcTemplate)
        NewScenarioRunner runner = new NewScenarioRunner(http, db, userId)

        when:
        ParitySnapshot actual = runner.run(scenarioId)
        def result = ParityComparator.compare(
                golden.scenario, golden.expectation, golden.divergentFields, golden.snapshot, actual)

        then:
        result.pass
        // 失敗時はresult.messageにフィールド単位の差分(field=... golden(legacy)=... actual(new)=...)が出る(AC-neg1)

        where:
        scenarioId << ["orders-list", "order-detail-own", "order-detail-missing"]
    }
}
