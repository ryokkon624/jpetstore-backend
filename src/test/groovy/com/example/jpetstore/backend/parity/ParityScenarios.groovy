package com.example.jpetstore.backend.parity

/**
 * L2パリティのシナリオ台帳（#48 AC5・#49 AC1〜AC5・design.md §2）。
 *
 * <p>シナリオは抽象識別子で記述し、両側（{@code capture/LegacyScenarioRunner}・
 * {@code verify/NewScenarioRunner}）がそれぞれの実体へ写像する。
 *
 * <p>#49で読み取り系6本（R1〜R6）と状態変更系2本（W2/W3）を追加した（横展開＝台帳への行追加のみ・
 * canonicalモデル/コンパレータ/golden スキーマ/gradleタスクは無変更）。
 * R2（カテゴリ配下の商品一覧）・R5（検索）・R6（検索・ワイルドカード文字）はいずれも複数の内部手順
 * （複数カテゴリ・複数キーワード）を1本のシナリオに畳み込み、結果は{@code entries}に discriminator
 * （{@code categoryId}/{@code query}）付きで格納する（golden本数を8本に抑えるための設計）。
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
            // #49 読み取り系（AC1/AC2/AC3）
            new Scenario("categories", EQUIVALENT),
            new Scenario("products-by-category", EQUIVALENT),
            new Scenario("items-by-product", EQUIVALENT),
            new Scenario("item-detail", EQUIVALENT),
            new Scenario("search-basic", EQUIVALENT),
            new Scenario("search-wildcard", "INTENDED_DIVERGENCE(ID-29)", ["entries"]),
            // #49 状態変更系（AC4/AC5）
            new Scenario("order-multi-item", EQUIVALENT),
            // 旧は成功し在庫デルタ/注文件数/合計/明細を持つが、新は失敗しこれらがすべて空になるため、
            // outcome/inventoryDeltaだけでなく成功時専用フィールド全てが宣言外の差分として扱われて
            // しまわないよう明示的に列挙する(Q4: 宣言と実測の完全一致が要求されるため)。
            new Scenario("order-insufficient-stock", "INTENDED_DIVERGENCE(ID-1)",
                    ["outcome", "inventoryDelta[EST-1]", "ordersCreated", "orderTotal",
                     "lines[EST-1].quantity", "lines[EST-1].unitPrice"]),
    ].asImmutable()

    static Scenario byId(String id) {
        Scenario found = ALL.find { it.id == id }
        if (found == null) {
            throw new IllegalArgumentException("未登録のシナリオID: ${id}")
        }
        return found
    }
}
