package com.example.jpetstore.backend.application.service

import com.example.jpetstore.backend.domain.cart.CartLine
import com.example.jpetstore.backend.domain.exception.ResourceNotFoundException
import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.domain.security.CurrentUserProvider
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartHeaderCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartItemCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartItemWriteCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ItemForCartCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.CartCustomMapper
import spock.lang.Specification

/**
 * #4 カートユースケース（add/update/remove/view/merge）を検証する。
 * AC2（数量0以下は行削除）・AC5/AC-neg1（在庫切れ追加不可・数量上限=在庫数をserver強制）・
 * [L2]（小計サーバ計算）・ID-19/計画フェーズ確定②（ログインマージ=加算+在庫クランプ）を実証する。
 */
class CartApplicationServiceSpec extends Specification {

    private static final Long USER_ID = 1L
    private static final Long CART_ID = 100L

    CartCustomMapper cartCustomMapper = Mock()
    CurrentUserProvider currentUserProvider = Stub() {
        requireCurrentUser() >> new AuthenticatedUser(USER_ID, "cart_user", ["USER"])
    }

    CartApplicationService service = new CartApplicationService(cartCustomMapper, currentUserProvider)

    void setupEnsureCart() {
        cartCustomMapper.ensureCart(_ as CartHeaderCustomEntity) >> { CartHeaderCustomEntity header ->
            header.cartId = CART_ID
        }
    }

    private static CartItemCustomEntity cartItemEntity(
            String itemId, String productId, String productName, String attribute1,
            BigDecimal listPrice, int quantity, int stockQuantity) {
        def e = new CartItemCustomEntity()
        e.itemId = itemId
        e.productId = productId
        e.productName = productName
        e.attribute1 = attribute1
        e.listPrice = listPrice
        e.quantity = quantity
        e.stockQuantity = stockQuantity
        e
    }

    private static ItemForCartCustomEntity itemForCart(String itemId, int stockQuantity, int currentQuantity) {
        def e = new ItemForCartCustomEntity()
        e.itemId = itemId
        e.stockQuantity = stockQuantity
        e.currentQuantity = currentQuantity
        e
    }

    def "viewCart: ensureCartでカートを確保しselectCartItemsの内容から小計をサーバ計算する([L2])"() {
        given:
        setupEnsureCart()
        cartCustomMapper.selectCartItems(CART_ID) >> [
                cartItemEntity("EST-1", "FI-SW-01", "Angelfish", "Large", 16.50, 2, 100),
                cartItemEntity("EST-22", "K9-RT-02", "Labrador Retriever", "Adult Male", 135.50, 1, 100),
        ]

        when:
        def cart = service.viewCart()

        then:
        cart.cartId() == CART_ID
        cart.items().size() == 2
        // 16.50*2 + 135.50*1 = 168.50
        cart.subtotal() == 168.50
    }

    def "viewCart: 在庫減で数量が在庫を超えた既存行はexceedsStock=trueのバッジ警告になる(強制はしない・計画③)"() {
        given:
        setupEnsureCart()
        cartCustomMapper.selectCartItems(CART_ID) >> [
                cartItemEntity("EST-2", "FI-SW-01", "Angelfish", "Small", 16.50, 5, 1),
        ]

        when:
        def cart = service.viewCart()

        then:
        cart.items()[0].quantity() == 5
        cart.items()[0].exceedsStock()
    }

    def "addItem: 未知のitemIdはResourceNotFoundExceptionになる(AC4/SBD-10)"() {
        given:
        setupEnsureCart()
        cartCustomMapper.selectItemForCart("NOPE", CART_ID) >> null

        when:
        service.addItem("NOPE", 1)

        then:
        thrown(ResourceNotFoundException)
        0 * cartCustomMapper.upsertCartItemQuantity(_)
    }

    def "AC5: addItemは在庫切れ(stock=0)アイテムを追加できない(400相当のIllegalArgumentException)"() {
        given:
        setupEnsureCart()
        cartCustomMapper.selectItemForCart("EST-3", CART_ID) >> itemForCart("EST-3", 0, 0)

        when:
        service.addItem("EST-3", 1)

        then:
        thrown(IllegalArgumentException)
        0 * cartCustomMapper.upsertCartItemQuantity(_)
    }

    def "AC-neg1: addItemは既存数量+追加数量が在庫数を超えると拒否する(server強制・qty非露出)"() {
        given:
        setupEnsureCart()
        cartCustomMapper.selectItemForCart("EST-2", CART_ID) >> itemForCart("EST-2", 1, 0)

        when: "在庫1に対し2個の追加を要求"
        service.addItem("EST-2", 2)

        then:
        thrown(IllegalArgumentException)
        0 * cartCustomMapper.upsertCartItemQuantity(_)
    }

    def "addItemは在庫内なら既存数量に加算した絶対値でupsertする"() {
        given:
        setupEnsureCart()
        cartCustomMapper.selectItemForCart("EST-1", CART_ID) >> itemForCart("EST-1", 100, 2)
        cartCustomMapper.selectCartItems(CART_ID) >> []

        when:
        service.addItem("EST-1", 3)

        then:
        1 * cartCustomMapper.upsertCartItemQuantity({ CartItemWriteCustomEntity w ->
            w.cartId == CART_ID && w.itemId == "EST-1" && w.quantity == 5 &&
                    w.createUserId == USER_ID && w.updateUserId == USER_ID
        })
    }

    def "SBD-2(sec指摘): addItemはrequestedQuantity<=0(#quantity)を拒否し、DBへ一切書き込まない"() {
        when:
        service.addItem("EST-1", quantity)

        then:
        thrown(IllegalArgumentException)
        0 * cartCustomMapper.ensureCart(_)
        0 * cartCustomMapper.selectItemForCart(_, _)
        0 * cartCustomMapper.upsertCartItemQuantity(_)

        where:
        quantity << [0, -1, -100, Integer.MIN_VALUE]
    }

    def "SBD-2(sec指摘): addItemは既存数量との合算がintをオーバーフローする場合、400相当の例外を投げ負の数量を永続化しない"() {
        given:
        setupEnsureCart()
        cartCustomMapper.selectItemForCart("EST-1", CART_ID) >> itemForCart("EST-1", 100, 1)

        when: "既存数量1にInteger.MAX_VALUEを加算するとintがオーバーフローしIntegerMIN_VALUE付近へラップする"
        service.addItem("EST-1", Integer.MAX_VALUE)

        then:
        thrown(IllegalArgumentException)
        0 * cartCustomMapper.upsertCartItemQuantity(_)
    }

    def "updateItem: 未知のitemIdはResourceNotFoundExceptionになる(AC4/SBD-10)"() {
        given:
        setupEnsureCart()
        cartCustomMapper.selectItemForCart("NOPE", CART_ID) >> null

        when:
        service.updateItem("NOPE", 1)

        then:
        thrown(ResourceNotFoundException)
    }

    def "AC2: updateItemは数量0以下で行削除する(map/list一貫の単一削除経路・幽霊行=ID-17を踏襲しない)"() {
        given:
        setupEnsureCart()
        cartCustomMapper.selectItemForCart("EST-1", CART_ID) >> itemForCart("EST-1", 100, 3)
        cartCustomMapper.selectCartItems(CART_ID) >> []

        when:
        service.updateItem("EST-1", 0)

        then:
        1 * cartCustomMapper.deleteCartItem(CART_ID, "EST-1")
        0 * cartCustomMapper.upsertCartItemQuantity(_)
    }

    def "AC-neg1: updateItemは在庫数を超える絶対値を拒否する(server強制)"() {
        given:
        setupEnsureCart()
        cartCustomMapper.selectItemForCart("EST-2", CART_ID) >> itemForCart("EST-2", 1, 1)

        when:
        service.updateItem("EST-2", 2)

        then:
        thrown(IllegalArgumentException)
        0 * cartCustomMapper.upsertCartItemQuantity(_)
    }

    def "updateItemは在庫内の正の絶対値をそのままupsertする"() {
        given:
        setupEnsureCart()
        cartCustomMapper.selectItemForCart("EST-1", CART_ID) >> itemForCart("EST-1", 100, 2)
        cartCustomMapper.selectCartItems(CART_ID) >> []

        when:
        service.updateItem("EST-1", 7)

        then:
        1 * cartCustomMapper.upsertCartItemQuantity({ CartItemWriteCustomEntity w -> w.quantity == 7 })
    }

    def "removeItemはdeleteCartItemを呼ぶ(冪等・存在しない行でも例外にしない)"() {
        given:
        setupEnsureCart()
        cartCustomMapper.selectCartItems(CART_ID) >> []

        when:
        service.removeItem("EST-1")

        then:
        1 * cartCustomMapper.deleteCartItem(CART_ID, "EST-1")
    }

    def "計画②: mergeはclientとserverの数量を加算し在庫数でクランプする"() {
        given:
        setupEnsureCart()
        cartCustomMapper.selectItemForCart("EST-1", CART_ID) >> itemForCart("EST-1", 100, 3)
        cartCustomMapper.selectCartItems(CART_ID) >> []

        when: "client側2個をマージ(server側既存3個+2=5、在庫100なのでクランプ無し)"
        service.merge([new CartLine("EST-1", 2)])

        then:
        1 * cartCustomMapper.upsertCartItemQuantity({ CartItemWriteCustomEntity w -> w.itemId == "EST-1" && w.quantity == 5 })
    }

    def "計画②: mergeは合算が在庫数を超える場合は例外にせず在庫数にクランプする"() {
        given:
        setupEnsureCart()
        cartCustomMapper.selectItemForCart("EST-2", CART_ID) >> itemForCart("EST-2", 1, 0)
        cartCustomMapper.selectCartItems(CART_ID) >> []

        when: "client側3個・在庫1個のみ"
        service.merge([new CartLine("EST-2", 3)])

        then:
        1 * cartCustomMapper.upsertCartItemQuantity({ CartItemWriteCustomEntity w -> w.itemId == "EST-2" && w.quantity == 1 })
    }

    def "mergeは未知のitemIdを例外にせず無視して他の行を処理する(クライアントの古いローカルデータへの防御)"() {
        given:
        setupEnsureCart()
        cartCustomMapper.selectItemForCart("NOPE", CART_ID) >> null
        cartCustomMapper.selectItemForCart("EST-1", CART_ID) >> itemForCart("EST-1", 100, 0)
        cartCustomMapper.selectCartItems(CART_ID) >> []

        when:
        service.merge([new CartLine("NOPE", 1), new CartLine("EST-1", 2)])

        then:
        noExceptionThrown()
        1 * cartCustomMapper.upsertCartItemQuantity({ CartItemWriteCustomEntity w -> w.itemId == "EST-1" && w.quantity == 2 })
        0 * cartCustomMapper.upsertCartItemQuantity({ CartItemWriteCustomEntity w -> w.itemId == "NOPE" })
    }

    def "mergeは在庫0のアイテムはクランプ結果0となり行削除する"() {
        given:
        setupEnsureCart()
        cartCustomMapper.selectItemForCart("EST-3", CART_ID) >> itemForCart("EST-3", 0, 0)
        cartCustomMapper.selectCartItems(CART_ID) >> []

        when:
        service.merge([new CartLine("EST-3", 2)])

        then:
        1 * cartCustomMapper.deleteCartItem(CART_ID, "EST-3")
        0 * cartCustomMapper.upsertCartItemQuantity(_)
    }

    def "SBD-2(sec指摘): mergeは合算がintをオーバーフローしても例外にせず在庫数へクランプする(非拒否方針を維持)"() {
        given:
        setupEnsureCart()
        cartCustomMapper.selectItemForCart("EST-1", CART_ID) >> itemForCart("EST-1", 100, 1)
        cartCustomMapper.selectCartItems(CART_ID) >> []

        when: "既存数量1にInteger.MAX_VALUEをマージするとintがオーバーフローする"
        service.merge([new CartLine("EST-1", Integer.MAX_VALUE)])

        then:
        noExceptionThrown()
        1 * cartCustomMapper.upsertCartItemQuantity({ CartItemWriteCustomEntity w -> w.itemId == "EST-1" && w.quantity == 100 })
    }

    def "#5 AC2(計画フェーズ確定②): mergeはquantity<=0(#quantity)の行を黙殺せず例外を投げる(黙殺廃止・400相当)"() {
        given:
        setupEnsureCart()

        when:
        service.merge([new CartLine("EST-1", quantity)])

        then:
        thrown(IllegalArgumentException)
        0 * cartCustomMapper.selectItemForCart(_, _)
        0 * cartCustomMapper.upsertCartItemQuantity(_)

        where:
        quantity << [0, -1, -100]
    }
}
