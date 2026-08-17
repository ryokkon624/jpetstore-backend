package com.example.jpetstore.backend.application.service

import com.example.jpetstore.backend.domain.exception.InsufficientStockException
import com.example.jpetstore.backend.domain.order.OrderAddress
import com.example.jpetstore.backend.domain.order.PlaceOrderCommand
import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.domain.security.CurrentUserProvider
import com.example.jpetstore.backend.infrastructure.audit.AuditLogRecorder
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartHeaderCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartItemCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.OrderHeaderWriteCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.CartCustomMapper
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.InventoryCustomMapper
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.OrderCustomMapper
import spock.lang.Specification

/**
 * #8: 注文確定ユースケース（サーバ再計算・在庫の原子的引当・成功/失敗監査）を検証する。
 * AC1(SBD-2)・AC2/AC3(arch §4.1)・AC6(SBD-14)・AC-neg1〜3を実証する。
 */
class OrderApplicationServiceSpec extends Specification {

    private static final Long USER_ID = 7L
    private static final Long CART_ID = 55L

    CartCustomMapper cartCustomMapper = Mock()
    OrderCustomMapper orderCustomMapper = Mock()
    InventoryCustomMapper inventoryCustomMapper = Mock()
    AuditLogRecorder auditLogRecorder = Mock()
    CurrentUserProvider currentUserProvider = Stub() {
        requireCurrentUser() >> new AuthenticatedUser(USER_ID, "order_user", ["USER"])
    }

    OrderApplicationService service = new OrderApplicationService(
            cartCustomMapper, orderCustomMapper, inventoryCustomMapper, currentUserProvider, auditLogRecorder)

    private static OrderAddress address(String firstName = "Taro") {
        new OrderAddress(firstName, "Yamada", "1 Test St", null, "Testville", "CA", "90000", "USA")
    }

    private static CartItemCustomEntity cartItem(String itemId, int quantity, BigDecimal listPrice) {
        def e = new CartItemCustomEntity()
        e.itemId = itemId
        e.productId = "P-${itemId}"
        e.productName = "Product ${itemId}"
        e.attribute1 = null
        e.listPrice = listPrice
        e.quantity = quantity
        e.stockQuantity = 100
        e
    }

    void setup() {
        cartCustomMapper.ensureCart(_ as CartHeaderCustomEntity) >> { CartHeaderCustomEntity h -> h.cartId = CART_ID }
    }

    def "AC1/AC-neg1: 合計はクライアント値を無視しΣ(listPrice×quantity)でサーバ再計算される"() {
        given:
        cartCustomMapper.selectCartItems(CART_ID) >> [
                cartItem("EST-1", 2, 16.50),
                cartItem("EST-22", 1, 135.50),
        ]
        inventoryCustomMapper.decreaseInventory(_) >> 1
        orderCustomMapper.insertOrderHeader(_) >> { OrderHeaderWriteCustomEntity h -> h.orderId = 100L }

        when:
        def confirmation = service.placeOrder(new PlaceOrderCommand(address(), null, false))

        then:
        confirmation.orderId() == 100L
        confirmation.totalPrice() == 168.50
    }

    def "AC2/AC3(arch §4.1): item_id昇順でガード減算と明細INSERTが行われる"() {
        given:
        cartCustomMapper.selectCartItems(CART_ID) >> [
                cartItem("EST-1", 1, 10.00),
                cartItem("EST-2", 2, 5.00),
        ]
        orderCustomMapper.insertOrderHeader(_) >> { OrderHeaderWriteCustomEntity h -> h.orderId = 200L }

        when:
        service.placeOrder(new PlaceOrderCommand(address(), null, false))

        then: "EST-1のガード減算が先"
        1 * inventoryCustomMapper.decreaseInventory({ it.itemId == "EST-1" && it.quantity == 1 }) >> 1

        then: "EST-1の明細(line_num=1)"
        1 * orderCustomMapper.insertOrderLine({ it.itemId == "EST-1" && it.lineNum == 1 && it.orderId == 200L })

        then: "EST-2のガード減算が後"
        1 * inventoryCustomMapper.decreaseInventory({ it.itemId == "EST-2" && it.quantity == 2 }) >> 1

        then: "EST-2の明細(line_num=2)"
        1 * orderCustomMapper.insertOrderLine({ it.itemId == "EST-2" && it.lineNum == 2 && it.orderId == 200L })
    }

    def "AC2/AC-neg2: 在庫不足(affected rows=0)はInsufficientStockExceptionになり明細・カートクリアは行われない"() {
        given:
        cartCustomMapper.selectCartItems(CART_ID) >> [cartItem("EST-3", 5, 10.00)]
        orderCustomMapper.insertOrderHeader(_) >> { OrderHeaderWriteCustomEntity h -> h.orderId = 300L }
        inventoryCustomMapper.decreaseInventory(_) >> 0

        when:
        service.placeOrder(new PlaceOrderCommand(address(), null, false))

        then:
        def e = thrown(InsufficientStockException)
        e.itemId() == "EST-3"
        0 * orderCustomMapper.insertOrderLine(_)
        0 * cartCustomMapper.deleteCartItems(_)
    }

    def "計画フェーズ確定②: 空カートはInsufficientStockException(itemId=null)で拒否され注文ヘッダを作らない"() {
        given:
        cartCustomMapper.selectCartItems(CART_ID) >> []

        when:
        service.placeOrder(new PlaceOrderCommand(address(), null, false))

        then:
        def e = thrown(InsufficientStockException)
        e.itemId() == null
        0 * orderCustomMapper.insertOrderHeader(_)
    }

    def "AC6: 成功時はカートを全クリアしSUCCESS監査を記録する"() {
        given:
        cartCustomMapper.selectCartItems(CART_ID) >> [cartItem("EST-1", 1, 10.00)]
        orderCustomMapper.insertOrderHeader(_) >> { OrderHeaderWriteCustomEntity h -> h.orderId = 400L }
        inventoryCustomMapper.decreaseInventory(_) >> 1

        when:
        service.placeOrder(new PlaceOrderCommand(address(), null, false))

        then:
        1 * cartCustomMapper.deleteCartItems(CART_ID)
        1 * auditLogRecorder.recordStateChange("ORDER_CREATE", "ORDER", "400", "SUCCESS", _)
        0 * auditLogRecorder.recordStateChangeIndependently(*_)
    }

    def "AC6(計画フェーズ確定③): 在庫不足時はREQUIRES_NEWの別tx経路でFAILURE監査を記録する"() {
        given:
        cartCustomMapper.selectCartItems(CART_ID) >> [cartItem("EST-3", 5, 10.00)]
        orderCustomMapper.insertOrderHeader(_) >> { OrderHeaderWriteCustomEntity h -> h.orderId = 500L }
        inventoryCustomMapper.decreaseInventory(_) >> 0

        when:
        service.placeOrder(new PlaceOrderCommand(address(), null, false))

        then:
        thrown(InsufficientStockException)
        1 * auditLogRecorder.recordStateChangeIndependently("ORDER_CREATE", null, null, "FAILURE", _)
        0 * auditLogRecorder.recordStateChange(*_)
    }

    def "AC6: 空カート拒否時もREQUIRES_NEWの別tx経路でFAILURE監査を記録する"() {
        given:
        cartCustomMapper.selectCartItems(CART_ID) >> []

        when:
        service.placeOrder(new PlaceOrderCommand(address(), null, false))

        then:
        thrown(InsufficientStockException)
        1 * auditLogRecorder.recordStateChangeIndependently("ORDER_CREATE", null, null, "FAILURE", _)
    }

    def "AC-neg3: 注文は認証プリンシパルのuserIdに紐づく(リクエストにusernameフィールドは無い)"() {
        given:
        cartCustomMapper.selectCartItems(CART_ID) >> [cartItem("EST-1", 1, 10.00)]
        inventoryCustomMapper.decreaseInventory(_) >> 1

        when:
        service.placeOrder(new PlaceOrderCommand(address(), null, false))

        then:
        1 * orderCustomMapper.insertOrderHeader({
            it.userId == USER_ID && it.createUserId == USER_ID && it.updateUserId == USER_ID
        }) >> { OrderHeaderWriteCustomEntity h -> h.orderId = 600L }
    }

    def "useSeparateShipping=falseはbillingを配送先として使う"() {
        given:
        cartCustomMapper.selectCartItems(CART_ID) >> [cartItem("EST-1", 1, 10.00)]
        inventoryCustomMapper.decreaseInventory(_) >> 1
        def billing = address("BillFirst")

        when:
        service.placeOrder(new PlaceOrderCommand(billing, null, false))

        then:
        1 * orderCustomMapper.insertOrderHeader({
            it.shipToFirstName == "BillFirst" && it.billToFirstName == "BillFirst"
        }) >> { OrderHeaderWriteCustomEntity h -> h.orderId = 700L }
    }

    def "useSeparateShipping=trueはshippingを配送先として使う"() {
        given:
        cartCustomMapper.selectCartItems(CART_ID) >> [cartItem("EST-1", 1, 10.00)]
        inventoryCustomMapper.decreaseInventory(_) >> 1
        def billing = address("BillFirst")
        def shipping = address("ShipFirst")

        when:
        service.placeOrder(new PlaceOrderCommand(billing, shipping, true))

        then:
        1 * orderCustomMapper.insertOrderHeader({
            it.shipToFirstName == "ShipFirst" && it.billToFirstName == "BillFirst"
        }) >> { OrderHeaderWriteCustomEntity h -> h.orderId = 800L }
    }

    def "未認証(CurrentUserProviderが空)はAccessDeniedExceptionになりカート読取も行わない"() {
        given:
        def unauthenticatedProvider = Stub(CurrentUserProvider) {
            requireCurrentUser() >> { throw new org.springframework.security.access.AccessDeniedException("Authentication required") }
        }
        def unauthenticatedService = new OrderApplicationService(
                cartCustomMapper, orderCustomMapper, inventoryCustomMapper, unauthenticatedProvider, auditLogRecorder)

        when:
        unauthenticatedService.placeOrder(new PlaceOrderCommand(address(), null, false))

        then:
        thrown(org.springframework.security.access.AccessDeniedException)
        0 * cartCustomMapper.selectCartItems(_)
    }
}
