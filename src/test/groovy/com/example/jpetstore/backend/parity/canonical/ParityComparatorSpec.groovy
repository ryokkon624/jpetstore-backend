package com.example.jpetstore.backend.parity.canonical

import spock.lang.Specification

/**
 * {@link ParityComparator} の判定4分岐＋失敗メッセージ書式（#48 AC4・AC-neg1）のUT。Docker不要。
 */
class ParityComparatorSpec extends Specification {

    private static ParitySnapshot w1(String unitPrice = "16.50") {
        return new ParitySnapshot(
                outcome: "SUCCESS",
                inventoryDelta: ["EST-1": -2],
                ordersCreated: 1,
                orderTotal: "33.00",
                lines: [new ParitySnapshot.Line(itemId: "EST-1", quantity: 2, unitPrice: unitPrice)])
    }

    def "EQUIVALENT宣言かつ差分なしはpassする"() {
        given:
        def golden = w1()
        def actual = w1()

        when:
        def result = ParityComparator.compare("order-single-item", "EQUIVALENT", [], golden, actual)

        then:
        result.pass
        result.diffs.isEmpty()
    }

    def "EQUIVALENT宣言かつ差分ありはfailし、どのフィールドが食い違ったか出力から判別できる(AC-neg1)"() {
        given:
        def golden = w1("16.50")
        def actual = w1("16.99")

        when:
        def result = ParityComparator.compare("order-single-item", "EQUIVALENT", [], golden, actual)

        then:
        !result.pass
        result.diffs.size() == 1
        result.diffs[0].field == "lines[EST-1].unitPrice"
        result.diffs[0].legacyValue == "16.50"
        result.diffs[0].newValue == "16.99"
        result.message.contains("scenario=order-single-item")
        result.message.contains("lines[EST-1].unitPrice")
        result.message.contains("16.50")
        result.message.contains("16.99")
    }

    def "INTENDED_DIVERGENCE宣言かつ差分なしはfailする(台帳の形骸化を検知)"() {
        given:
        def golden = w1()
        def actual = w1()

        when:
        def result = ParityComparator.compare(
                "order-insufficient-stock", "INTENDED_DIVERGENCE(ID-1)", ["outcome", "inventoryDelta[EST-1]"], golden, actual)

        then:
        !result.pass
        result.message.contains("台帳の形骸化")
    }

    def "INTENDED_DIVERGENCE宣言かつdivergentFieldsと一致する差分はpassする"() {
        given:
        def golden = new ParitySnapshot(outcome: "SUCCESS", inventoryDelta: ["EST-1": -1])
        def actual = new ParitySnapshot(outcome: "FAILURE", inventoryDelta: ["EST-1": 0])

        when:
        def result = ParityComparator.compare(
                "order-insufficient-stock", "INTENDED_DIVERGENCE(ID-1)",
                ["outcome", "inventoryDelta[EST-1]"], golden, actual)

        then:
        result.pass
        result.diffs*.field as Set == ["outcome", "inventoryDelta[EST-1]"] as Set
    }

    def "INTENDED_DIVERGENCE宣言でも宣言外の差分が混ざればfailする(Q4確定)"() {
        given:
        def golden = new ParitySnapshot(outcome: "SUCCESS", inventoryDelta: ["EST-1": -1], ordersCreated: 1)
        def actual = new ParitySnapshot(outcome: "FAILURE", inventoryDelta: ["EST-1": 0], ordersCreated: 0)

        when:
        // divergentFieldsはoutcomeのみ宣言。inventoryDelta/ordersCreatedの差分は宣言外。
        def result = ParityComparator.compare(
                "order-insufficient-stock", "INTENDED_DIVERGENCE(ID-1)", ["outcome"], golden, actual)

        then:
        !result.pass
        result.message.contains("宣言外の差分")
        result.message.contains("ordersCreated")
    }

    // ---- #51 T1: アカウント系canonicalフィールドの差分粒度(AC6) ----

    def "accountはキー単位で差分が出る(account[email]粒度・inventoryDelta[EST-1]の先例踏襲)"() {
        given:
        def golden = new ParitySnapshot(account: ["username": "parity_w4", "email": "old@example.com"])
        def actual = new ParitySnapshot(account: ["username": "parity_w4", "email": "new@example.com"])

        when:
        def result = ParityComparator.compare("account-register", "EQUIVALENT", [], golden, actual)

        then:
        !result.pass
        result.diffs.size() == 1
        result.diffs[0].field == "account[email]"
        result.diffs[0].legacyValue == "old@example.com"
        result.diffs[0].newValue == "new@example.com"
    }

    def "account/accountsCreatedが一致すればEQUIVALENTはpassする"() {
        given:
        def account = ["username": "parity_w4", "email": "a@example.com"]
        def golden = new ParitySnapshot(account: account, accountsCreated: 1)
        def actual = new ParitySnapshot(account: new HashMap<>(account), accountsCreated: 1)

        expect:
        ParityComparator.compare("account-register", "EQUIVALENT", [], golden, actual).pass
    }

    def "accountsCreatedの差分はスカラとして検出される"() {
        given:
        def golden = new ParitySnapshot(accountsCreated: 1)
        def actual = new ParitySnapshot(accountsCreated: 0)

        when:
        def result = ParityComparator.compare("account-register", "EQUIVALENT", [], golden, actual)

        then:
        !result.pass
        result.diffs*.field == ["accountsCreated"]
    }

    def "R8b: httpStatus/stackTraceExposedがdivergentFieldsと完全一致すればpassする(SM-3・ID-14の証拠固定化)"() {
        given:
        def golden = new ParitySnapshot(httpStatus: 500, stackTraceExposed: true)
        def actual = new ParitySnapshot(httpStatus: 403, stackTraceExposed: false)

        when:
        def result = ParityComparator.compare(
                "order-detail-missing", "INTENDED_DIVERGENCE(ID-14)",
                ["httpStatus", "stackTraceExposed"], golden, actual)

        then:
        result.pass
        result.diffs*.field as Set == ["httpStatus", "stackTraceExposed"] as Set
    }

    def "R8b: 旧が500+スタックトレース露出のままなら宣言と実測が一致せずfailする(台帳の形骸化)"() {
        given:
        def golden = new ParitySnapshot(httpStatus: 500, stackTraceExposed: true)
        def actual = new ParitySnapshot(httpStatus: 500, stackTraceExposed: true)

        when:
        def result = ParityComparator.compare(
                "order-detail-missing", "INTENDED_DIVERGENCE(ID-14)",
                ["httpStatus", "stackTraceExposed"], golden, actual)

        then:
        !result.pass
        result.message.contains("台帳の形骸化")
    }

    def "R8a: lines[EST-1].productNameの差分がdivergentFieldsと一致すればpassする(Q6・ID-24)"() {
        given:
        def golden = new ParitySnapshot(lines: [
                new ParitySnapshot.Line(itemId: "EST-1", quantity: 2, unitPrice: "16.50", productName: "")])
        def actual = new ParitySnapshot(lines: [
                new ParitySnapshot.Line(itemId: "EST-1", quantity: 2, unitPrice: "16.50", productName: "Angelfish")])

        when:
        def result = ParityComparator.compare(
                "order-detail-own", "INTENDED_DIVERGENCE(ID-24)", ["lines[EST-1].productName"], golden, actual)

        then:
        result.pass
        result.diffs*.field == ["lines[EST-1].productName"]
    }
}
