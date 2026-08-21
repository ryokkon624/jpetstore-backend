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

/**
 * カート境界値（cart-boundary）の新側verify（#51 AC4・優先度は最後・dual-tag）。
 *
 * <p>{@code Cart}/{@code CartItem}の未踏分岐（{@code removeItemById}の2アウトカム）を境界値で踏む。
 * {@code demo_user}フィクスチャは{@link ParityUserFixture}を{@code OrderParitySpec}と共用する。
 */
@Tag("integration")
@Tag("parity")
class CartParitySpec extends ParityIntegrationTestBase {

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

    def "cart-boundary: 新側がcommit済みgoldenと宣言どおりの結果になる(#51 AC4/AC7)"() {
        given:
        ParityGolden golden = ParityGoldenIO.readFromClasspath("cart-boundary")
        NewDbReader db = new NewDbReader(jdbcTemplate)
        NewScenarioRunner runner = new NewScenarioRunner(http, db, userId)

        when:
        ParitySnapshot actual = runner.run("cart-boundary")
        def result = ParityComparator.compare(
                golden.scenario, golden.expectation, golden.divergentFields, golden.snapshot, actual)

        then:
        result.pass
        // 失敗時はresult.messageにフィールド単位の差分(field=... golden(legacy)=... actual(new)=...)が出る(AC-neg1)
    }
}
