package com.example.jpetstore.backend.infrastructure.mybatis.order

import com.example.jpetstore.backend.domain.enums.OrderStatus
import com.example.jpetstore.backend.domain.order.NewOrder
import com.example.jpetstore.backend.domain.order.OrderAddress
import com.example.jpetstore.backend.domain.order.OrderLine
import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.domain.security.CurrentUserProvider
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.OrderHeaderWriteCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.OrderLineWriteCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.OrderCustomMapper
import spock.lang.Specification

import java.time.LocalDate

/**
 * #30: {@link MyBatisOrderRepository} が {@link OrderCustomMapper} を各メソッド1回だけ呼び、生成された注文IDを返す・
 * WHOカラム（create/update_user_id）を{@link CurrentUserProvider}から解決することを純UT（Mapper mock・DB非依存）で
 * 検証する。
 */
class MyBatisOrderRepositorySpec extends Specification {

    private static final Long USER_ID = 7L
    private static final Long ORDER_ID = 100L

    OrderCustomMapper orderCustomMapper = Mock()
    CurrentUserProvider currentUserProvider = Stub() {
        requireCurrentUser() >> new AuthenticatedUser(USER_ID, "order_user", ["USER"])
    }

    MyBatisOrderRepository repository = new MyBatisOrderRepository(orderCustomMapper, currentUserProvider)

    private static OrderAddress address(String firstName = "Taro") {
        new OrderAddress(firstName, "Yamada", "1 Test St", null, "Testville", "CA", "90000", "USA")
    }

    def "insertHeader: orderCustomMapper#insertOrderHeaderを1回呼び生成IDを返し、WHOをcurrentUserProviderから補完する"() {
        given:
        def order = new NewOrder(USER_ID, address("Bill"), address("Ship"), 100.0G, OrderStatus.NEW, LocalDate.of(2026, 1, 1))

        when:
        def orderId = repository.insertHeader(order)

        then:
        1 * orderCustomMapper.insertOrderHeader({ OrderHeaderWriteCustomEntity h ->
            h.userId == USER_ID && h.createUserId == USER_ID && h.updateUserId == USER_ID &&
                    h.billToFirstName == "Bill" && h.shipToFirstName == "Ship" &&
                    h.statusCode == "NEW" && h.totalPrice == 100.0G
        }) >> { OrderHeaderWriteCustomEntity h -> h.orderId = ORDER_ID }
        orderId == ORDER_ID
    }

    def "insertLine: orderCustomMapper#insertOrderLineを1回だけ呼ぶ"() {
        given:
        def line = new OrderLine("EST-1", 1, 5, 10.00G)

        when:
        repository.insertLine(ORDER_ID, line)

        then:
        1 * orderCustomMapper.insertOrderLine({ OrderLineWriteCustomEntity l ->
            l.orderId == ORDER_ID && l.itemId == "EST-1" && l.lineNum == 1 && l.quantity == 5 &&
                    l.unitPrice == 10.00G && l.createUserId == USER_ID && l.updateUserId == USER_ID
        })
    }
}
