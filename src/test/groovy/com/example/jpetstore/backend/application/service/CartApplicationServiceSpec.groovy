package com.example.jpetstore.backend.application.service

import com.example.jpetstore.backend.domain.cart.Cart
import com.example.jpetstore.backend.domain.cart.CartItem
import com.example.jpetstore.backend.domain.cart.CartLine
import com.example.jpetstore.backend.domain.cart.CartRepository
import com.example.jpetstore.backend.domain.cart.StockAvailability
import com.example.jpetstore.backend.domain.exception.ResourceNotFoundException
import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.domain.security.CurrentUserProvider
import spock.lang.Specification

/**
 * #4/#29 カートユースケース（add/update/remove/view/merge）のオーケストレーションを検証する（#29
 * D2=案A・DB非依存の薄いUT。{@link CartRepository} をmockし、不変条件そのもの（在庫上限・overflow・mergeクランプ・0削除）は
 * {@code CartSpec}/{@code CartItemSpec}（純ドメインUT）へ移設済み）。
 */
class CartApplicationServiceSpec extends Specification {

    private static final Long USER_ID = 1L
    private static final Long CART_ID = 100L

    CartRepository cartRepository = Mock()
    CurrentUserProvider currentUserProvider = Stub() {
        requireCurrentUser() >> new AuthenticatedUser(USER_ID, "cart_user", ["USER"])
    }

    CartApplicationService service = new CartApplicationService(cartRepository, currentUserProvider)

    private static Cart emptyCart() {
        Cart.reconstruct(CART_ID, [])
    }

    private static StockAvailability stock(String itemId, int stockQuantity, int currentQuantity) {
        new StockAvailability(itemId, stockQuantity, currentQuantity)
    }

    def "viewCartはCartRepository#findByUserIdの結果をそのまま返す([L2]の小計計算自体はCartが担う)"() {
        given:
        def cart = Cart.reconstruct(CART_ID, [
                CartItem.reconstruct("EST-1", "FI-SW-01", "Angelfish", "Large", 2, 16.50, 100),
        ])
        cartRepository.findByUserId(USER_ID) >> cart

        expect:
        service.viewCart() == cart
    }

    def "addItem: 未知のitemIdはResourceNotFoundExceptionになる(AC4/SBD-10)"() {
        given:
        cartRepository.findByUserId(USER_ID) >> emptyCart()
        cartRepository.findStock(CART_ID, "NOPE") >> Optional.empty()

        when:
        service.addItem("NOPE", 1)

        then:
        thrown(ResourceNotFoundException)
        0 * cartRepository.upsertItem(_, _)
    }

    def "addItem: Cartのコマンドメソッドが在庫関連の例外を投げた場合はそのまま伝播しupsertItemを呼ばない(AC5/AC-neg1)"() {
        given:
        cartRepository.findByUserId(USER_ID) >> emptyCart()
        cartRepository.findStock(CART_ID, "EST-3") >> Optional.of(stock("EST-3", 0, 0))

        when:
        service.addItem("EST-3", 1)

        then:
        thrown(IllegalArgumentException)
        0 * cartRepository.upsertItem(_, _)
    }

    def "addItemは在庫内なら既存数量に加算した絶対値をupsertItemへ渡し、更新後カートを再取得して返す"() {
        given:
        def updatedCart = Cart.reconstruct(CART_ID, [])
        cartRepository.findByUserId(USER_ID) >>> [emptyCart(), updatedCart]
        cartRepository.findStock(CART_ID, "EST-1") >> Optional.of(stock("EST-1", 100, 2))

        when:
        def result = service.addItem("EST-1", 3)

        then:
        1 * cartRepository.upsertItem(CART_ID, { CartItem line -> line.itemId() == "EST-1" && line.quantity() == 5 })
        result == updatedCart
    }

    def "SBD-2(sec指摘): addItemはrequestedQuantity<=0(#quantity)を拒否し、Repositoryへ一切アクセスしない"() {
        when:
        service.addItem("EST-1", quantity)

        then:
        thrown(IllegalArgumentException)
        0 * cartRepository.findByUserId(_)
        0 * cartRepository.findStock(_, _)
        0 * cartRepository.upsertItem(_, _)

        where:
        quantity << [0, -1, -100, Integer.MIN_VALUE]
    }

    def "updateItem: 未知のitemIdはResourceNotFoundExceptionになる(AC4/SBD-10)"() {
        given:
        cartRepository.findByUserId(USER_ID) >> emptyCart()
        cartRepository.findStock(CART_ID, "NOPE") >> Optional.empty()

        when:
        service.updateItem("NOPE", 1)

        then:
        thrown(ResourceNotFoundException)
    }

    def "AC2: updateItemは数量0以下でCartRepository#removeItemを呼ぶ(単一削除経路・幽霊行=ID-17を踏襲しない)"() {
        given:
        cartRepository.findByUserId(USER_ID) >>> [emptyCart(), emptyCart()]
        cartRepository.findStock(CART_ID, "EST-1") >> Optional.of(stock("EST-1", 100, 3))

        when:
        service.updateItem("EST-1", 0)

        then:
        1 * cartRepository.removeItem(CART_ID, "EST-1")
        0 * cartRepository.upsertItem(_, _)
    }

    def "AC-neg1: updateItemは在庫数を超える絶対値でCartのコマンドメソッドが例外を投げるとupsertItemを呼ばない"() {
        given:
        cartRepository.findByUserId(USER_ID) >> emptyCart()
        cartRepository.findStock(CART_ID, "EST-2") >> Optional.of(stock("EST-2", 1, 1))

        when:
        service.updateItem("EST-2", 2)

        then:
        thrown(IllegalArgumentException)
        0 * cartRepository.upsertItem(_, _)
    }

    def "updateItemは在庫内の正の絶対値をそのままupsertItemへ渡す"() {
        given:
        cartRepository.findByUserId(USER_ID) >>> [emptyCart(), emptyCart()]
        cartRepository.findStock(CART_ID, "EST-1") >> Optional.of(stock("EST-1", 100, 2))

        when:
        service.updateItem("EST-1", 7)

        then:
        1 * cartRepository.upsertItem(CART_ID, { CartItem line -> line.quantity() == 7 })
    }

    def "removeItemはCartRepository#removeItemを呼ぶ(冪等・存在しない行でも例外にしない)"() {
        given:
        cartRepository.findByUserId(USER_ID) >>> [emptyCart(), emptyCart()]

        when:
        service.removeItem("EST-1")

        then:
        1 * cartRepository.removeItem(CART_ID, "EST-1")
    }

    def "計画②: mergeはclientとserverの数量を加算し在庫数でクランプしてupsertItemへ渡す"() {
        given:
        cartRepository.findByUserId(USER_ID) >>> [emptyCart(), emptyCart()]
        cartRepository.findStock(CART_ID, "EST-1") >> Optional.of(stock("EST-1", 100, 3))

        when: "client側2個をマージ(server側既存3個+2=5、在庫100なのでクランプ無し)"
        service.merge([new CartLine("EST-1", 2)])

        then:
        1 * cartRepository.upsertItem(CART_ID, { CartItem line -> line.itemId() == "EST-1" && line.quantity() == 5 })
    }

    def "mergeは未知のitemId(findStockがempty)を例外にせず無視して他の行を処理する"() {
        given:
        cartRepository.findByUserId(USER_ID) >>> [emptyCart(), emptyCart()]
        cartRepository.findStock(CART_ID, "NOPE") >> Optional.empty()
        cartRepository.findStock(CART_ID, "EST-1") >> Optional.of(stock("EST-1", 100, 0))

        when:
        service.merge([new CartLine("NOPE", 1), new CartLine("EST-1", 2)])

        then:
        noExceptionThrown()
        1 * cartRepository.upsertItem(CART_ID, { CartItem line -> line.itemId() == "EST-1" && line.quantity() == 2 })
        0 * cartRepository.upsertItem(CART_ID, { CartItem line -> line.itemId() == "NOPE" })
    }

    def "mergeは在庫0のアイテムはクランプ結果0となりCartRepository#removeItemを呼ぶ"() {
        given:
        cartRepository.findByUserId(USER_ID) >>> [emptyCart(), emptyCart()]
        cartRepository.findStock(CART_ID, "EST-3") >> Optional.of(stock("EST-3", 0, 0))

        when:
        service.merge([new CartLine("EST-3", 2)])

        then:
        1 * cartRepository.removeItem(CART_ID, "EST-3")
        0 * cartRepository.upsertItem(_, _)
    }

    def "#5 AC2(計画フェーズ確定②): mergeはquantity<=0(#quantity)の行を黙殺せず例外を投げ、findStockを呼ばない"() {
        given:
        cartRepository.findByUserId(USER_ID) >> emptyCart()

        when:
        service.merge([new CartLine("EST-1", quantity)])

        then:
        thrown(IllegalArgumentException)
        0 * cartRepository.findStock(_, _)
        0 * cartRepository.upsertItem(_, _)

        where:
        quantity << [0, -1, -100]
    }
}
