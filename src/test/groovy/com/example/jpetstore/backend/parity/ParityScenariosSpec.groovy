package com.example.jpetstore.backend.parity

import com.example.jpetstore.backend.parity.canonical.ParityGoldenIO
import spock.lang.Specification

/**
 * シナリオ台帳の機械的整合性チェック（#48 AC8／#49 AC-neg2の機械化）。Docker不要（golden読み込みはclasspath経由）。
 *
 * <p>台帳に登録された全シナリオについて、対応するgolden JSONがコミット済みであり、
 * {@code capturedFrom.legacyCommit}/{@code capturedAt} が埋まっていることを検証する。
 * golden未採取のうちはRED（{@code captureGolden}実行・コミットでGREENになる）。
 */
class ParityScenariosSpec extends Specification {

    def "台帳の全シナリオにgoldenが存在し、declareしたexpectationとgolden側expectationが一致する"() {
        expect:
        !ParityScenarios.ALL.isEmpty()

        and:
        ParityScenarios.ALL.every { scenario ->
            def golden = ParityGoldenIO.readFromClasspath(scenario.id)
            golden.scenario == scenario.id && golden.expectation == scenario.expectation
        }
    }

    def "台帳の全シナリオのgoldenにcapturedFrom.legacyCommit/capturedAtが埋まっている(AC8)"() {
        expect:
        ParityScenarios.ALL.every { scenario ->
            def golden = ParityGoldenIO.readFromClasspath(scenario.id)
            golden.capturedFrom != null &&
                    !golden.capturedFrom.legacyCommit.isBlank() &&
                    !golden.capturedFrom.capturedAt.isBlank()
        }
    }

    def "INTENDED_DIVERGENCE宣言のシナリオはgolden側のdivergentFieldsと台帳の宣言が一致する"() {
        expect:
        ParityScenarios.ALL.findAll { it.isIntendedDivergence() }.every { scenario ->
            def golden = ParityGoldenIO.readFromClasspath(scenario.id)
            golden.divergentFields as Set == scenario.divergentFields as Set
        }
    }
}
