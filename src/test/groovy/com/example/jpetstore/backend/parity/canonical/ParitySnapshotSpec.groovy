package com.example.jpetstore.backend.parity.canonical

import spock.lang.Specification
import spock.lang.Unroll

/**
 * {@link ParitySnapshot#normalize()} の正規化ルール（#48 AC1/AC2/AC3）のUT。Docker不要（無タグ＝{@code test}タスク）。
 */
class ParitySnapshotSpec extends Specification {

    def "inventoryDeltaはキー昇順に正規化される"() {
        given:
        def snapshot = new ParitySnapshot(inventoryDelta: ["EST-3": -1, "EST-1": -2, "EST-2": 1])

        when:
        def normalized = snapshot.normalize()

        then:
        normalized.inventoryDelta.keySet() as List == ["EST-1", "EST-2", "EST-3"]
        normalized.inventoryDelta == ["EST-1": -2, "EST-2": 1, "EST-3": -1]
    }

    @Unroll
    def "orderTotalはscale(2)の文字列へ正規化される(#input -> #expected)"() {
        expect:
        ParitySnapshot.normalizeAmount(input) == expected

        where:
        input     | expected
        "33"      | "33.00"
        "33.5"    | "33.50"
        "33.005"  | "33.01"
        "0"       | "0.00"
        null      | null
    }

    def "linesはitemId昇順に正規化されunitPriceもscale(2)になる"() {
        given:
        def snapshot = new ParitySnapshot(lines: [
                new ParitySnapshot.Line(itemId: "EST-2", quantity: 1, unitPrice: "5"),
                new ParitySnapshot.Line(itemId: "EST-1", quantity: 2, unitPrice: "16.5"),
        ])

        when:
        def normalized = snapshot.normalize()

        then:
        normalized.lines*.itemId == ["EST-1", "EST-2"]
        normalized.lines[0].unitPrice == "16.50"
        normalized.lines[1].unitPrice == "5.00"
    }

    def "entriesは各エントリのcanonicalキー昇順に正規化される(並び順は保存すべき業務ロジックに含めない・AC3)"() {
        given:
        def snapshot = new ParitySnapshot(entries: [
                ["categoryId": "REPTILES"],
                ["categoryId": "BIRDS"],
                ["categoryId": "CATS"],
        ])

        when:
        def normalized = snapshot.normalize()

        then:
        normalized.entries*.categoryId == ["BIRDS", "CATS", "REPTILES"]
    }

    def "normalizeは元のインスタンスを変更しない(呼び出し元の状態を破壊しない)"() {
        given:
        def original = new ParitySnapshot(inventoryDelta: ["EST-2": 1, "EST-1": -2])

        when:
        original.normalize()

        then:
        original.inventoryDelta.keySet() as List == ["EST-2", "EST-1"]
    }

    def "同一itemId/quantity/unitPriceのLineは等価と判定される(比較で使うため)"() {
        expect:
        new ParitySnapshot.Line(itemId: "EST-1", quantity: 2, unitPrice: "16.50") ==
                new ParitySnapshot.Line(itemId: "EST-1", quantity: 2, unitPrice: "16.50")
    }

    // ---- #51 T1: アカウント系canonicalフィールドの正規化 ----

    def "accountはキー昇順のTreeMapへ正規化される(AC1・確定1のcanonical14項目)"() {
        given:
        def snapshot = new ParitySnapshot(account: ["username": "parity_w4", "email": "a@example.com"])

        when:
        def normalized = snapshot.normalize()

        then:
        normalized.account.keySet() as List == ["email", "username"]
        normalized.account == ["email": "a@example.com", "username": "parity_w4"]
    }

    def "accountが未設定(null)でも正規化は空Mapを返す(他シナリオへのノイズ防止)"() {
        given:
        def snapshot = new ParitySnapshot()

        when:
        def normalized = snapshot.normalize()

        then:
        normalized.account == [:]
    }

    def "accountsCreated/httpStatus/stackTraceExposedはそのまま保持される(スカラ比較用)"() {
        given:
        def snapshot = new ParitySnapshot(accountsCreated: 1, httpStatus: 403, stackTraceExposed: true)

        when:
        def normalized = snapshot.normalize()

        then:
        normalized.accountsCreated == 1
        normalized.httpStatus == 403
        normalized.stackTraceExposed == true
    }

    def "Lineのproductnameは正規化で保持される(Q6・ID-24の観測点)"() {
        given:
        def snapshot = new ParitySnapshot(lines: [
                new ParitySnapshot.Line(itemId: "EST-1", quantity: 2, unitPrice: "16.50", productName: ""),
        ])

        when:
        def normalized = snapshot.normalize()

        then:
        normalized.lines[0].productName == ""
    }

    def "同一itemId/quantity/unitPriceでもproductNameが違えばLineは等価ではない(Q6の差分検知に必要)"() {
        expect:
        new ParitySnapshot.Line(itemId: "EST-1", quantity: 2, unitPrice: "16.50", productName: "") !=
                new ParitySnapshot.Line(itemId: "EST-1", quantity: 2, unitPrice: "16.50", productName: "Angelfish")
    }
}
