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
}
