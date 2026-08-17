package com.example.jpetstore.backend.domain.cart

import spock.lang.Specification

/**
 * #29 AC3: Cart集約のコマンドメソッド（addItem/updateItem/mergeLine）が不変条件
 * （在庫上限・Math.addExactオーバーフロー・mergeクランプ・数量0以下の削除シグナル）を強制することを純ドメインUTで検証する。
 * CartApplicationServiceからDBに依存しないロジックとして移設した（旧CartApplicationServiceSpecの同名テストを移設）。
 */
class CartSpec extends Specification {

    private static final Long CART_ID = 100L

    def "subtotalはitemsのlineTotal合算のサーバ計算([L2])"() {
        given:
        def items = [
                CartItem.reconstruct("EST-1", "FI-SW-01", "Angelfish", "Large", 2, 16.50, 100),
                CartItem.reconstruct("EST-22", "K9-RT-02", "Labrador Retriever", "Adult Male", 1, 135.50, 100),
        ]
        def cart = Cart.reconstruct(CART_ID, items)

        expect:
        // 16.50*2 + 135.50*1 = 168.50
        cart.subtotal() == 168.50
    }

    def "addItem: requestedQuantity<=0(#quantity)は拒否する(SBD-2)"() {
        given:
        def cart = Cart.reconstruct(CART_ID, [])
        def stock = new StockAvailability("EST-1", 100, 0)

        when:
        cart.addItem(stock, quantity)

        then:
        thrown(IllegalArgumentException)

        where:
        quantity << [0, -1, -100, Integer.MIN_VALUE]
    }

    def "AC5: addItemは在庫切れ(stock=0)アイテムを追加できない"() {
        given:
        def cart = Cart.reconstruct(CART_ID, [])
        def stock = new StockAvailability("EST-3", 0, 0)

        when:
        cart.addItem(stock, 1)

        then:
        thrown(IllegalArgumentException)
    }

    def "AC-neg1: addItemは既存数量+追加数量が在庫数を超えると拒否する(server強制・qty非露出)"() {
        given:
        def cart = Cart.reconstruct(CART_ID, [])
        def stock = new StockAvailability("EST-2", 1, 0)

        when: "在庫1に対し2個の追加を要求"
        cart.addItem(stock, 2)

        then:
        thrown(IllegalArgumentException)
    }

    def "addItemは在庫内なら既存数量に加算した絶対値のCartItemを返す"() {
        given:
        def cart = Cart.reconstruct(CART_ID, [])
        def stock = new StockAvailability("EST-1", 100, 2)

        when:
        def result = cart.addItem(stock, 3)

        then:
        result.itemId() == "EST-1"
        result.quantity() == 5
    }

    def "SBD-2(sec指摘): addItemは既存数量との合算がintをオーバーフローする場合、拒否し負の数量を作らない"() {
        given:
        def cart = Cart.reconstruct(CART_ID, [])
        def stock = new StockAvailability("EST-1", 100, 1)

        when: "既存数量1にInteger.MAX_VALUEを加算するとintがオーバーフローする"
        cart.addItem(stock, Integer.MAX_VALUE)

        then:
        thrown(IllegalArgumentException)
    }

    def "AC2: updateItemは数量0以下で削除シグナル(Optional.empty)を返す"() {
        given:
        def cart = Cart.reconstruct(CART_ID, [])
        def stock = new StockAvailability("EST-1", 100, 3)

        when:
        def result = cart.updateItem(stock, 0)

        then:
        result.isEmpty()
    }

    def "AC-neg1: updateItemは在庫数を超える絶対値を拒否する"() {
        given:
        def cart = Cart.reconstruct(CART_ID, [])
        def stock = new StockAvailability("EST-2", 1, 1)

        when:
        cart.updateItem(stock, 2)

        then:
        thrown(IllegalArgumentException)
    }

    def "updateItemは在庫内の正の絶対値をそのままCartItemに反映する"() {
        given:
        def cart = Cart.reconstruct(CART_ID, [])
        def stock = new StockAvailability("EST-1", 100, 2)

        when:
        def result = cart.updateItem(stock, 7)

        then:
        result.isPresent()
        result.get().quantity() == 7
    }

    def "計画②: mergeLineはclientとserverの数量を加算し在庫数でクランプする"() {
        given:
        def cart = Cart.reconstruct(CART_ID, [])
        def stock = new StockAvailability("EST-1", 100, 3)

        when: "client側2個をマージ(server側既存3個+2=5、在庫100なのでクランプ無し)"
        def result = cart.mergeLine(stock, 2)

        then:
        result.isPresent()
        result.get().quantity() == 5
    }

    def "計画②: mergeLineは合算が在庫数を超える場合は例外にせず在庫数にクランプする"() {
        given:
        def cart = Cart.reconstruct(CART_ID, [])
        def stock = new StockAvailability("EST-2", 1, 0)

        when: "client側3個・在庫1個のみ"
        def result = cart.mergeLine(stock, 3)

        then:
        result.isPresent()
        result.get().quantity() == 1
    }

    def "mergeLineは在庫0のアイテムはクランプ結果0となり削除シグナル(Optional.empty)を返す"() {
        given:
        def cart = Cart.reconstruct(CART_ID, [])
        def stock = new StockAvailability("EST-3", 0, 0)

        when:
        def result = cart.mergeLine(stock, 2)

        then:
        result.isEmpty()
    }

    def "SBD-2(sec指摘): mergeLineは合算がintをオーバーフローしても例外にせず在庫数へクランプする(非拒否方針を維持)"() {
        given:
        def cart = Cart.reconstruct(CART_ID, [])
        def stock = new StockAvailability("EST-1", 100, 1)

        when: "既存数量1にInteger.MAX_VALUEをマージするとintがオーバーフローする"
        def result = cart.mergeLine(stock, Integer.MAX_VALUE)

        then:
        result.isPresent()
        result.get().quantity() == 100
    }

    def "#5 AC2(計画フェーズ確定②): mergeLineはquantity<=0(#quantity)を黙殺せず例外を投げる(黙殺廃止・400相当)"() {
        given:
        def cart = Cart.reconstruct(CART_ID, [])
        def stock = new StockAvailability("EST-1", 100, 0)

        when:
        cart.mergeLine(stock, quantity)

        then:
        thrown(IllegalArgumentException)

        where:
        quantity << [0, -1, -100]
    }
}
