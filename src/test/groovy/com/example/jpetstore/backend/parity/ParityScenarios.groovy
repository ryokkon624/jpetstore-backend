package com.example.jpetstore.backend.parity

/**
 * L2パリティのシナリオ台帳（#48 AC5・design.md §2）。
 *
 * <p>シナリオは抽象識別子で記述し、両側（{@code capture/LegacyScenarioRunner}・
 * {@code verify/NewScenarioRunner}）がそれぞれの実体へ写像する。本Storyでは W1（{@code order-single-item}）
 * 1本を登録する。R1〜R6・W2・W3は#49で追加する（横展開＝台帳への行追加のみ・機構自体は変更しない）。
 */
class ParityScenarios {

    /** {@code EQUIVALENT}（旧同値） */
    static final String EQUIVALENT = "EQUIVALENT"

    static class Scenario {
        final String id
        final String expectation
        final List<String> divergentFields

        Scenario(String id, String expectation, List<String> divergentFields = []) {
            this.id = id
            this.expectation = expectation
            this.divergentFields = divergentFields.asImmutable()
        }

        /** {@code INTENDED_DIVERGENCE(ID-x)} 宣言かどうか。 */
        boolean isIntendedDivergence() {
            return expectation != EQUIVALENT
        }
    }

    static final List<Scenario> ALL = [
            new Scenario("order-single-item", EQUIVALENT),
    ].asImmutable()

    static Scenario byId(String id) {
        Scenario found = ALL.find { it.id == id }
        if (found == null) {
            throw new IllegalArgumentException("未登録のシナリオID: ${id}")
        }
        return found
    }
}
