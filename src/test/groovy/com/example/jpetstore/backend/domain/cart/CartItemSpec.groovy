package com.example.jpetstore.backend.domain.cart

import com.example.jpetstore.backend.domain.enums.StockStatus
import spock.lang.Specification

/**
 * #29 D1（案A）: CartItemはstockQuantityを private 内部保持し、外向きは
 * stockStatus()/exceedsStock()/lineTotal()/quantity() の射影アクセサのみ公開する（生stock getterを持たない＝ID-28を型で強制）。
 */
class CartItemSpec extends Specification {

    def "reconstructは表示用フィールドをそのまま保持する"() {
        when:
        def item = CartItem.reconstruct("EST-1", "FI-SW-01", "Angelfish", "Large", 2, 16.50, 100)

        then:
        item.itemId() == "EST-1"
        item.productId() == "FI-SW-01"
        item.productName() == "Angelfish"
        item.attribute1() == "Large"
        item.quantity() == 2
        item.listPrice() == 16.50
    }

    def "lineTotalはlistPrice×quantityのサーバ計算([L2])"() {
        given:
        def item = CartItem.reconstruct("EST-1", "FI-SW-01", "Angelfish", "Large", 3, 16.50, 100)

        expect:
        item.lineTotal() == 49.50
    }

    def "stockStatusは内部保持したstockQuantityから算出する(在庫qty自体は非露出)"() {
        given:
        def outOfStock = CartItem.reconstruct("EST-3", "FI-SW-02", "Goldfish", null, 1, 5.00, 0)

        expect:
        outOfStock.stockStatus() == StockStatus.OUT_OF_STOCK
    }

    def "exceedsStockはquantityが内部stockQuantityを超える場合にtrueになる(強制はしない・バッジ警告用)"() {
        given:
        def exceeding = CartItem.reconstruct("EST-2", "FI-SW-01", "Angelfish", "Small", 5, 16.50, 1)
        def within = CartItem.reconstruct("EST-2", "FI-SW-01", "Angelfish", "Small", 1, 16.50, 100)

        expect:
        exceeding.exceedsStock()
        !within.exceedsStock()
    }
}
