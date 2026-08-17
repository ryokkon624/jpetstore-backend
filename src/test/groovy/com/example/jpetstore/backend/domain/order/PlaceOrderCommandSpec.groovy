package com.example.jpetstore.backend.domain.order

import spock.lang.Specification

/**
 * #8: PlaceOrderCommand#effectiveShipping()の配送先解決ロジック（依存の無い純粋関数）を検証する。
 */
class PlaceOrderCommandSpec extends Specification {

    private static OrderAddress address(String firstName) {
        new OrderAddress(firstName, "Yamada", "1 Test St", null, "Testville", "CA", "90000", "USA")
    }

    def "useSeparateShipping=falseはshippingを無視しbillingを配送先として使う"() {
        given:
        def billing = address("Bill")
        def shipping = address("Ship")

        expect:
        new PlaceOrderCommand(billing, shipping, false).effectiveShipping() == billing
    }

    def "useSeparateShipping=trueかつshippingありはshippingを配送先として使う"() {
        given:
        def billing = address("Bill")
        def shipping = address("Ship")

        expect:
        new PlaceOrderCommand(billing, shipping, true).effectiveShipping() == shipping
    }

    def "useSeparateShipping=trueだがshipping未指定(null)はbillingへフォールバックする(防御的)"() {
        given:
        def billing = address("Bill")

        expect:
        new PlaceOrderCommand(billing, null, true).effectiveShipping() == billing
    }
}
