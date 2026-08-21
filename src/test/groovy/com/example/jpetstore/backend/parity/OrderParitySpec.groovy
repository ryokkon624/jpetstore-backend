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
 * 注文確定系（W1/W2/W3）の新側verify（#48 AC9・AC12・#49 AC4/AC5・dual-tag）。
 *
 * <p>コミット済みgolden（legacy採取済み）とのみ比較する。legacyの起動は不要（AC12）。
 * {@code USER_PRIMARY}（D3）は{@link ParityUserFixture}（#51 T1で共通化）で {@code m_account}/
 * {@code m_signon}/{@code m_profile} へ {@code demo_user}/{@code Sprint3-DemoLogin!26} を用意する
 * （{@code R__test_user.sql}は同期しない）。
 * W1={@code order-single-item}(EQUIVALENT)・W2={@code order-multi-item}(EQUIVALENT)・
 * W3={@code order-insufficient-stock}(INTENDED_DIVERGENCE(ID-1))。
 */
@Tag("integration")
@Tag("parity")
class OrderParitySpec extends ParityIntegrationTestBase {

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
    def "#scenarioId: 新側がcommit済みgoldenと宣言どおりの結果になる(#48 AC9/AC12・#49 AC7)"() {
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
        scenarioId << ["order-single-item", "order-multi-item", "order-insufficient-stock"]
    }
}
