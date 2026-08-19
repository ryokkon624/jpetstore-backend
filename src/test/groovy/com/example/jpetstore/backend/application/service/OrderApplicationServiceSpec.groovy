package com.example.jpetstore.backend.application.service

import com.example.jpetstore.backend.domain.cart.Cart
import com.example.jpetstore.backend.domain.cart.CartItem
import com.example.jpetstore.backend.domain.cart.CartRepository
import com.example.jpetstore.backend.domain.exception.InsufficientStockException
import com.example.jpetstore.backend.domain.inventory.InventoryRepository
import com.example.jpetstore.backend.domain.order.OrderAddress
import com.example.jpetstore.backend.domain.order.OrderDetailLine
import com.example.jpetstore.backend.domain.order.OrderHeader
import com.example.jpetstore.backend.domain.order.OrderRepository
import com.example.jpetstore.backend.domain.order.OrderSummary
import com.example.jpetstore.backend.domain.order.PlaceOrderCommand
import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.domain.security.CurrentUserProvider
import com.example.jpetstore.backend.domain.security.OwnershipAuthorizationService
import com.example.jpetstore.backend.infrastructure.audit.AuditLogRecorder
import org.springframework.security.access.AccessDeniedException
import spock.lang.Specification

import java.time.LocalDate

/**
 * #8/#30: 注文確定ユースケース（サーバ再計算・在庫の原子的引当・成功/失敗監査）を検証する。
 * AC1(SBD-2)・AC2/AC3(arch §4.1)・AC6(SBD-14)・AC-neg1〜3を実証する。
 *
 * <p>#30でCartCustomMapper/OrderCustomMapper/InventoryCustomMapper直呼びから{@link CartRepository}/
 * {@link OrderRepository}/{@link InventoryRepository}（Domain層）経由へretrofitした。並行オーケストレーション
 * （item_id昇順固定順ループ・ガード減算＋{@code AffectedRows.requireUpdated}・カート全クリア・成功/失敗監査）は
 * 引き続き本Serviceに残す（O1・案A）。Repositoryは単文アトミック委譲に純化したため、WHOカラム（create/update_user_id）
 * の検証は{@code MyBatisOrderRepositorySpec}/{@code MyBatisInventoryRepositorySpec}側で行う。
 */
class OrderApplicationServiceSpec extends Specification {

    private static final Long USER_ID = 7L
    private static final Long CART_ID = 55L

    CartRepository cartRepository = Mock()
    OrderRepository orderRepository = Mock()
    InventoryRepository inventoryRepository = Mock()
    AuditLogRecorder auditLogRecorder = Mock()
    CurrentUserProvider currentUserProvider = Stub() {
        requireCurrentUser() >> new AuthenticatedUser(USER_ID, "order_user", ["USER"])
    }
    OwnershipAuthorizationService ownershipAuthorizationService = new OwnershipAuthorizationService(currentUserProvider)

    OrderApplicationService service = new OrderApplicationService(
            cartRepository, orderRepository, inventoryRepository, currentUserProvider, auditLogRecorder,
            ownershipAuthorizationService)

    private static OrderAddress address(String firstName = "Taro") {
        new OrderAddress(firstName, "Yamada", "1 Test St", null, "Testville", "CA", "90000", "USA")
    }

    private static CartItem cartItem(String itemId, int quantity, BigDecimal listPrice) {
        CartItem.reconstruct(itemId, "P-${itemId}", "Product ${itemId}", null, quantity, listPrice, 100)
    }

    private static Cart cartOf(CartItem... items) {
        Cart.reconstruct(CART_ID, items as List)
    }

    void setup() {
        cartRepository.ensureCart(USER_ID) >> CART_ID
    }

    def "AC1/AC-neg1: 合計はクライアント値を無視しΣ(listPrice×quantity)でサーバ再計算される"() {
        given:
        cartRepository.findByCartId(CART_ID) >> cartOf(
                cartItem("EST-1", 2, 16.50),
                cartItem("EST-22", 1, 135.50),
        )
        inventoryRepository.decrease(_, _) >> 1
        orderRepository.insertHeader(_) >> 100L

        when:
        def confirmation = service.placeOrder(new PlaceOrderCommand(address(), null, false))

        then:
        confirmation.orderId() == 100L
        confirmation.totalPrice() == 168.50
    }

    def "AC2/AC3(arch §4.1): item_id昇順でガード減算と明細INSERTが行われる"() {
        given:
        cartRepository.findByCartId(CART_ID) >> cartOf(
                cartItem("EST-1", 1, 10.00),
                cartItem("EST-2", 2, 5.00),
        )
        orderRepository.insertHeader(_) >> 200L

        when:
        service.placeOrder(new PlaceOrderCommand(address(), null, false))

        then: "EST-1のガード減算が先"
        1 * inventoryRepository.decrease("EST-1", 1) >> 1

        then: "EST-1の明細(line_num=1)"
        1 * orderRepository.insertLine(200L, { it.itemId() == "EST-1" && it.lineNum() == 1 })

        then: "EST-2のガード減算が後"
        1 * inventoryRepository.decrease("EST-2", 2) >> 1

        then: "EST-2の明細(line_num=2)"
        1 * orderRepository.insertLine(200L, { it.itemId() == "EST-2" && it.lineNum() == 2 })
    }

    def "AC2/AC-neg2: 在庫不足(affected rows=0)はInsufficientStockExceptionになり明細・カートクリアは行われない"() {
        given:
        cartRepository.findByCartId(CART_ID) >> cartOf(cartItem("EST-3", 5, 10.00))
        orderRepository.insertHeader(_) >> 300L
        inventoryRepository.decrease(_, _) >> 0

        when:
        service.placeOrder(new PlaceOrderCommand(address(), null, false))

        then:
        def e = thrown(InsufficientStockException)
        e.itemId() == "EST-3"
        0 * orderRepository.insertLine(*_)
        0 * cartRepository.clearItems(_)
    }

    def "計画フェーズ確定②: 空カートはInsufficientStockException(itemId=null)で拒否され注文ヘッダを作らない"() {
        given:
        cartRepository.findByCartId(CART_ID) >> cartOf()

        when:
        service.placeOrder(new PlaceOrderCommand(address(), null, false))

        then:
        def e = thrown(InsufficientStockException)
        e.itemId() == null
        0 * orderRepository.insertHeader(_)
    }

    def "AC6: 成功時はカートを全クリアしSUCCESS監査を記録する"() {
        given:
        cartRepository.findByCartId(CART_ID) >> cartOf(cartItem("EST-1", 1, 10.00))
        orderRepository.insertHeader(_) >> 400L
        inventoryRepository.decrease(_, _) >> 1

        when:
        service.placeOrder(new PlaceOrderCommand(address(), null, false))

        then:
        1 * cartRepository.clearItems(CART_ID)
        1 * auditLogRecorder.recordStateChange("ORDER_CREATE", "ORDER", "400", "SUCCESS", _)
        0 * auditLogRecorder.recordStateChangeIndependently(*_)
    }

    def "AC6(計画フェーズ確定③): 在庫不足時はREQUIRES_NEWの別tx経路でFAILURE監査を記録する"() {
        given:
        cartRepository.findByCartId(CART_ID) >> cartOf(cartItem("EST-3", 5, 10.00))
        orderRepository.insertHeader(_) >> 500L
        inventoryRepository.decrease(_, _) >> 0

        when:
        service.placeOrder(new PlaceOrderCommand(address(), null, false))

        then:
        thrown(InsufficientStockException)
        1 * auditLogRecorder.recordStateChangeIndependently("ORDER_CREATE", null, null, "FAILURE", _)
        0 * auditLogRecorder.recordStateChange(*_)
    }

    def "#40 AC1(N3): 在庫不足以外の失敗(DB例外等)でもREQUIRES_NEWの別tx経路でFAILURE監査を記録してから再送出する"() {
        given:
        cartRepository.findByCartId(CART_ID) >> cartOf(cartItem("EST-1", 1, 10.00))
        orderRepository.insertHeader(_) >> { throw new org.springframework.dao.DataIntegrityViolationException("Data too long for column 'postal_code'") }

        when:
        service.placeOrder(new PlaceOrderCommand(address(), null, false))

        then: "DB由来の生メッセージ・例外クラス名は失敗detailに含めない(SBD-10)"
        thrown(org.springframework.dao.DataIntegrityViolationException)
        1 * auditLogRecorder.recordStateChangeIndependently("ORDER_CREATE", null, null, "FAILURE",
                { !it.toString().contains("postal_code") && !it.toString().contains("DataIntegrityViolationException") })
        0 * auditLogRecorder.recordStateChange(*_)
    }

    def "#40 AC1(N3・F3): cartRepository.ensureCart自体が失敗してもFAILURE監査を記録してから再送出する(tryの外だと監査ゼロで抜けていた経路)"() {
        given:
        cartRepository.ensureCart(USER_ID) >> { throw new RuntimeException("db down") }

        when:
        service.placeOrder(new PlaceOrderCommand(address(), null, false))

        then:
        thrown(RuntimeException)
        1 * auditLogRecorder.recordStateChangeIndependently("ORDER_CREATE", null, null, "FAILURE", _)
        0 * auditLogRecorder.recordStateChange(*_)
    }

    def "AC6: 空カート拒否時もREQUIRES_NEWの別tx経路でFAILURE監査を記録する"() {
        given:
        cartRepository.findByCartId(CART_ID) >> cartOf()

        when:
        service.placeOrder(new PlaceOrderCommand(address(), null, false))

        then:
        thrown(InsufficientStockException)
        1 * auditLogRecorder.recordStateChangeIndependently("ORDER_CREATE", null, null, "FAILURE", _)
    }

    def "AC-neg3: 注文は認証プリンシパルのuserIdに紐づく(リクエストにusernameフィールドは無い)"() {
        given:
        cartRepository.findByCartId(CART_ID) >> cartOf(cartItem("EST-1", 1, 10.00))
        inventoryRepository.decrease(_, _) >> 1

        when:
        service.placeOrder(new PlaceOrderCommand(address(), null, false))

        then:
        1 * orderRepository.insertHeader({ it.userId() == USER_ID }) >> 600L
    }

    def "useSeparateShipping=falseはbillingを配送先として使う"() {
        given:
        cartRepository.findByCartId(CART_ID) >> cartOf(cartItem("EST-1", 1, 10.00))
        inventoryRepository.decrease(_, _) >> 1
        def billing = address("BillFirst")

        when:
        service.placeOrder(new PlaceOrderCommand(billing, null, false))

        then:
        1 * orderRepository.insertHeader({
            it.shipping().firstName() == "BillFirst" && it.billing().firstName() == "BillFirst"
        }) >> 700L
    }

    def "useSeparateShipping=trueはshippingを配送先として使う"() {
        given:
        cartRepository.findByCartId(CART_ID) >> cartOf(cartItem("EST-1", 1, 10.00))
        inventoryRepository.decrease(_, _) >> 1
        def billing = address("BillFirst")
        def shipping = address("ShipFirst")

        when:
        service.placeOrder(new PlaceOrderCommand(billing, shipping, true))

        then:
        1 * orderRepository.insertHeader({
            it.shipping().firstName() == "ShipFirst" && it.billing().firstName() == "BillFirst"
        }) >> 800L
    }

    def "未認証(CurrentUserProviderが空)はAccessDeniedExceptionになりカート読取も行わない"() {
        given:
        def unauthenticatedProvider = Stub(CurrentUserProvider) {
            requireCurrentUser() >> { throw new org.springframework.security.access.AccessDeniedException("Authentication required") }
        }
        def unauthenticatedService = new OrderApplicationService(
                cartRepository, orderRepository, inventoryRepository, unauthenticatedProvider, auditLogRecorder,
                new OwnershipAuthorizationService(unauthenticatedProvider))

        when:
        unauthenticatedService.placeOrder(new PlaceOrderCommand(address(), null, false))

        then:
        thrown(org.springframework.security.access.AccessDeniedException)
        0 * cartRepository.ensureCart(_)
        0 * cartRepository.findByCartId(_)
    }

    def "#9 AC1/AC2: listOrdersは認証プリンシパルのuserIdでlistByUser/countByUserを呼びPageを組み立てる"() {
        given:
        def summaries = [new OrderSummary(2L, LocalDate.of(2026, 1, 2), 20.00G)]

        when:
        def page = service.listOrders(1, 12)

        then:
        1 * orderRepository.listByUser(USER_ID, 0, 12) >> summaries
        1 * orderRepository.countByUser(USER_ID) >> 1L
        page.content() == summaries
        page.page() == 1
        page.size() == 12
        page.totalElements() == 1L
    }

    def "#10 AC1/AC2: getOrderは所有者一致なら明細付きOrderDetailを返す"() {
        given:
        def header = new OrderHeader(9L, USER_ID, LocalDate.of(2026, 1, 9), 33.00G)
        def lines = [new OrderDetailLine("EST-1", "Angelfish", 2, 16.50G)]

        when:
        def detail = service.getOrder(9L)

        then:
        1 * orderRepository.findHeaderById(9L) >> Optional.of(header)
        1 * orderRepository.findLinesByOrderId(9L) >> lines

        and:
        detail.orderId() == 9L
        detail.orderDate() == LocalDate.of(2026, 1, 9)
        detail.totalPrice() == 33.00G
        detail.lines() == lines
    }

    def "#10 AC3/AC-neg2(SBD-8): 存在しないorderIdはAccessDeniedException(403)になり明細は読まない"() {
        when:
        service.getOrder(999L)

        then:
        1 * orderRepository.findHeaderById(999L) >> Optional.empty()
        thrown(AccessDeniedException)
        0 * orderRepository.findLinesByOrderId(_)
    }

    def "#10 AC-neg1(SBD-1): 他人のorderIdはAccessDeniedException(403)になり明細は読まない(同一403でnot-foundと区別不能)"() {
        given:
        def header = new OrderHeader(9L, USER_ID + 1, LocalDate.of(2026, 1, 9), 33.00G)

        when:
        service.getOrder(9L)

        then:
        1 * orderRepository.findHeaderById(9L) >> Optional.of(header)
        thrown(AccessDeniedException)
        0 * orderRepository.findLinesByOrderId(_)
    }

    def "perf: getOrderはfindHeaderByIdを1回だけ呼び、識別子解決用と最終応答用を分けない"() {
        given:
        def header = new OrderHeader(9L, USER_ID, LocalDate.of(2026, 1, 9), 33.00G)

        when:
        service.getOrder(9L)

        then:
        1 * orderRepository.findHeaderById(9L) >> Optional.of(header)
        1 * orderRepository.findLinesByOrderId(9L) >> []
    }
}
