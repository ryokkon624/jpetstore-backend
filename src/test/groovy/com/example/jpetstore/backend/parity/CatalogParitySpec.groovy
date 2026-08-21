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
import spock.lang.Tag
import spock.lang.Unroll

/**
 * 読み取り系（R1〜R6）の新側verify（#49 AC1〜AC3・dual-tag）。
 *
 * <p>R1〜R5はEQUIVALENT、R6（{@code search-wildcard}）はID-29の{@code INTENDED_DIVERGENCE}。
 * いずれも認証不要（{@code SecurityConfig}のGETスコープpermitAll）のため、本specは
 * {@code USER_PRIMARY}フィクスチャを用意しない（W系の{@code OrderParitySpec}と異なる）。
 * {@code NewScenarioRunner}の読み取り系メソッドは{@code userId}/{@code db}を参照しないため、
 * ダミー値（0L）を渡してもよい設計だが、将来の拡張に備え実際のBeanをそのまま渡す。
 */
@Tag("integration")
@Tag("parity")
class CatalogParitySpec extends ParityIntegrationTestBase {

    @Autowired
    JdbcTemplate jdbcTemplate

    @Unroll
    def "#scenarioId: 新側がcommit済みgoldenと宣言どおりの結果になる(#49 AC1〜AC3/AC7)"() {
        given:
        ParityGolden golden = ParityGoldenIO.readFromClasspath(scenarioId)
        NewHttpClient http = new NewHttpClient(baseUrl())
        NewScenarioRunner runner = new NewScenarioRunner(http, new NewDbReader(jdbcTemplate), 0L)

        when:
        ParitySnapshot actual = runner.run(scenarioId)
        def result = ParityComparator.compare(
                golden.scenario, golden.expectation, golden.divergentFields, golden.snapshot, actual)

        then:
        result.pass
        // 失敗時はresult.messageにフィールド単位の差分(field=... golden(legacy)=... actual(new)=...)が出る(AC-neg1)

        where:
        scenarioId << ["categories", "products-by-category", "items-by-product", "item-detail",
                        "search-basic", "search-wildcard"]
    }
}
