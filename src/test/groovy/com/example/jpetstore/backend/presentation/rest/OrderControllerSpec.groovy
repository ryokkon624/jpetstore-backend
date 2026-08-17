package com.example.jpetstore.backend.presentation.rest

import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.infrastructure.security.AuthCookieSupport
import com.example.jpetstore.backend.infrastructure.security.JwtService
import com.example.jpetstore.backend.support.IntegrationTestBase
import com.jayway.jsonpath.JsonPath
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Tag

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * #8: 注文確定API（{@code POST /api/orders}）のend-to-end実証。
 * AC1(SBD-2)・AC2/AC3(arch §4.1)・AC4(SBD-3)・AC5(SBD-13)・AC6(SBD-14)・[L2]・AC-neg1〜3を実証する。
 *
 * <p>本specは在庫を実際に減算するため、既存カタログseed（EST-*）は使わず専用アイテム（ZZ-ORDER-*）を使う
 * （他specのEST-*在庫前提を壊さないための隔離。実装ノート参照）。
 */
@Tag("integration")
@AutoConfigureMockMvc
class OrderControllerSpec extends IntegrationTestBase {

    private static final String USERNAME = "order_controller_test_user"
    private static final String ITEM_PLENTY = "ZZ-ORDER-1"
    private static final String ITEM_LOW = "ZZ-ORDER-2"

    private static final String BILLING_JSON = '''
        {"firstName":"Taro","lastName":"Yamada","address1":"1 Test St","address2":"Suite 2",
         "city":"Testville","state":"CA","postalCode":"90000","country":"USA"}
    '''

    @Autowired
    MockMvc mockMvc

    @Autowired
    JwtService jwtService

    @Autowired
    JdbcTemplate jdbcTemplate

    Long userId
    Cookie accessTokenCookie

    void setup() {
        cleanupFixtures()
        jdbcTemplate.update(
                """
                INSERT INTO m_account
                    (username, email, first_name, last_name, status, address1, city, state, postal_code, country, phone,
                     create_program, update_program)
                VALUES (?, ?, 'Order', 'ControllerTestUser', 'OK', '1 Test St', 'Testville', 'CA', '90000', 'USA', '555-0100',
                     'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                USERNAME, "${USERNAME}@example.com")
        userId = jdbcTemplate.queryForObject("SELECT user_id FROM m_account WHERE username = ?", Long, USERNAME)
        def user = new AuthenticatedUser(userId, USERNAME, ["USER"])
        accessTokenCookie = new Cookie(AuthCookieSupport.ACCESS_TOKEN_COOKIE, jwtService.generateAccessToken(user))

        insertItem(ITEM_PLENTY, new BigDecimal("10.00"), 100)
        insertItem(ITEM_LOW, new BigDecimal("20.00"), 1)
    }

    void cleanup() {
        cleanupFixtures()
    }

    private void cleanupFixtures() {
        jdbcTemplate.update("DELETE FROM t_audit_log WHERE action = 'ORDER_CREATE'")
        jdbcTemplate.update("DELETE FROM t_order_line WHERE item_id IN (?, ?)", ITEM_PLENTY, ITEM_LOW)
        jdbcTemplate.update("DELETE FROM t_order WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)", USERNAME)
        jdbcTemplate.update("DELETE FROM t_cart_item WHERE cart_id IN (SELECT cart_id FROM t_cart WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?))", USERNAME)
        jdbcTemplate.update("DELETE FROM t_cart WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)", USERNAME)
        jdbcTemplate.update("DELETE FROM m_account WHERE username = ?", USERNAME)
        jdbcTemplate.update("DELETE FROM t_inventory WHERE item_id IN (?, ?)", ITEM_PLENTY, ITEM_LOW)
        jdbcTemplate.update("DELETE FROM m_item WHERE item_id IN (?, ?)", ITEM_PLENTY, ITEM_LOW)
    }

    private void insertItem(String itemId, BigDecimal listPrice, int stock) {
        jdbcTemplate.update(
                """
                INSERT INTO m_item (item_id, product_id, list_price, unit_cost, status, attribute1,
                    create_program, update_program)
                VALUES (?, 'FI-SW-01', ?, ?, 'P', 'Test', 'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                itemId, listPrice, listPrice)
        jdbcTemplate.update(
                """
                INSERT INTO t_inventory (item_id, quantity, create_program, update_program)
                VALUES (?, ?, 'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                itemId, stock)
    }

    private void addToCart(String itemId, int quantity) {
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"itemId":"${itemId}","quantity":${quantity}}"""))
                .andExpect(status().isOk())
    }

    private static String placeOrderBody(String extraJson = null) {
        def extra = extraJson ? ",${extraJson}" : ""
        """{"billing": ${BILLING_JSON}, "useSeparateShipping": false${extra}}"""
    }

    def "AC1/AC4/AC5/[L2]: 認証済み+CSRFでPOST /api/ordersすると201でorderId/totalPriceを返しカートが全クリアされる"() {
        given:
        addToCart(ITEM_PLENTY, 2)

        expect:
        mockMvc.perform(post("/api/orders").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(placeOrderBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath('$.orderId').exists())
                .andExpect(jsonPath('$.totalPrice').value(20.00d))

        and: "カートが全クリアされる"
        mockMvc.perform(get("/api/cart").cookie(accessTokenCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items.length()').value(0))
    }

    def "AC2/AC3(arch §4.1): 注文確定後にt_order/t_order_line/t_inventoryへ正しく永続化される"() {
        given:
        addToCart(ITEM_PLENTY, 3)

        when:
        def result = mockMvc.perform(post("/api/orders").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(placeOrderBody()))
                .andExpect(status().isCreated())
                .andReturn()
        Long orderId = JsonPath.read(result.response.contentAsString, '$.orderId') as Long

        then:
        def order = jdbcTemplate.queryForMap("SELECT * FROM t_order WHERE order_id = ?", orderId)
        order.user_id == userId
        order.status_code == "NEW"
        order.total_price == new BigDecimal("30.00")
        order.bill_to_first_name == "Taro"
        order.ship_to_first_name == "Taro"

        def line = jdbcTemplate.queryForMap("SELECT * FROM t_order_line WHERE order_id = ?", orderId)
        line.item_id == ITEM_PLENTY
        line.quantity == 3
        line.unit_price == new BigDecimal("10.00")
        line.line_num == 1

        jdbcTemplate.queryForObject("SELECT quantity FROM t_inventory WHERE item_id = ?", Integer, ITEM_PLENTY) == 97
    }

    def "AC2/AC-neg2: 在庫不足(競合負けを含む)は409になり、注文は作られず在庫・カートも変化しない"() {
        given: "在庫1のアイテムを1個カートに追加した後、他の同時発注で在庫が0になった状況を再現"
        addToCart(ITEM_LOW, 1)
        jdbcTemplate.update("UPDATE t_inventory SET quantity = 0 WHERE item_id = ?", ITEM_LOW)

        expect:
        mockMvc.perform(post("/api/orders").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(placeOrderBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath('$.code').value("CONFLICT"))

        and: "在庫は負数化せず0のまま"
        jdbcTemplate.queryForObject("SELECT quantity FROM t_inventory WHERE item_id = ?", Integer, ITEM_LOW) == 0

        and: "注文は作られない"
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_order WHERE user_id = ?", Integer, userId) == 0

        and: "カートはクリアされない(明細が残る)"
        mockMvc.perform(get("/api/cart").cookie(accessTokenCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items.length()').value(1))
    }

    def "計画フェーズ確定②: 空カートでの確定要求は409になる"() {
        expect:
        mockMvc.perform(post("/api/orders").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(placeOrderBody()))
                .andExpect(status().isConflict())
    }

    def "住所欠落は400になる(billing必須項目の欠落)"() {
        expect:
        mockMvc.perform(post("/api/orders").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"billing":{"firstName":"Taro"},"useSeparateShipping":false}'))
                .andExpect(status().isBadRequest())
    }

    def "未認証でPOST /api/ordersすると401になる(CSRFフィルタは通過させたうえで認証の欠如を検証)"() {
        expect:
        mockMvc.perform(post("/api/orders").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(placeOrderBody()))
                .andExpect(status().isUnauthorized())
    }

    def "CSRFトークン無しでPOST /api/ordersすると403になる(AC4)"() {
        expect:
        mockMvc.perform(post("/api/orders").cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(placeOrderBody()))
                .andExpect(status().isForbidden())
    }

    def "AC-neg1(SBD-2): totalPriceを注入しても無視されサーバ再計算値が永続化される"() {
        given:
        addToCart(ITEM_PLENTY, 1)

        when:
        def result = mockMvc.perform(post("/api/orders").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(placeOrderBody('"totalPrice":0.01')))
                .andExpect(status().isCreated())
                .andExpect(jsonPath('$.totalPrice').value(10.00d))
                .andReturn()
        Long orderId = JsonPath.read(result.response.contentAsString, '$.orderId') as Long

        then:
        jdbcTemplate.queryForObject("SELECT total_price FROM t_order WHERE order_id = ?", BigDecimal, orderId) == new BigDecimal("10.00")
    }

    def "AC-neg3(SBD-1): usernameを注入しても無視され認証プリンシパル本人に紐づく"() {
        given:
        addToCart(ITEM_PLENTY, 1)

        when:
        def result = mockMvc.perform(post("/api/orders").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(placeOrderBody('"username":"attacker"')))
                .andExpect(status().isCreated())
                .andReturn()
        Long orderId = JsonPath.read(result.response.contentAsString, '$.orderId') as Long

        then:
        jdbcTemplate.queryForObject("SELECT user_id FROM t_order WHERE order_id = ?", Long, orderId) == userId
    }

    def "AC6: 成功時はresult=SUCCESSのORDER_CREATE監査行が残る"() {
        given:
        addToCart(ITEM_PLENTY, 1)

        when:
        def result = mockMvc.perform(post("/api/orders").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(placeOrderBody()))
                .andExpect(status().isCreated())
                .andReturn()
        Long orderId = JsonPath.read(result.response.contentAsString, '$.orderId') as Long

        then:
        def row = jdbcTemplate.queryForMap(
                "SELECT * FROM t_audit_log WHERE action = 'ORDER_CREATE' AND actor_user_id = ? ORDER BY audit_id DESC LIMIT 1",
                userId)
        row.result == "SUCCESS"
        row.target_type == "ORDER"
        row.target_id == orderId.toString()
    }

    def "AC6(計画フェーズ確定③): 在庫不足失敗時もresult=FAILUREのORDER_CREATE監査行が残る(主txロールバックに巻き込まれない)"() {
        given:
        addToCart(ITEM_LOW, 1)
        jdbcTemplate.update("UPDATE t_inventory SET quantity = 0 WHERE item_id = ?", ITEM_LOW)

        when:
        mockMvc.perform(post("/api/orders").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(placeOrderBody()))
                .andExpect(status().isConflict())

        then:
        def row = jdbcTemplate.queryForMap(
                "SELECT * FROM t_audit_log WHERE action = 'ORDER_CREATE' AND actor_user_id = ? ORDER BY audit_id DESC LIMIT 1",
                userId)
        row.result == "FAILURE"
        row.target_id == null
        row.detail.toString().contains("INSUFFICIENT_STOCK")
    }

    def "AC6: 空カート失敗時もresult=FAILUREのORDER_CREATE監査行が残る"() {
        when:
        mockMvc.perform(post("/api/orders").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(placeOrderBody()))
                .andExpect(status().isConflict())

        then:
        def row = jdbcTemplate.queryForMap(
                "SELECT * FROM t_audit_log WHERE action = 'ORDER_CREATE' AND actor_user_id = ? ORDER BY audit_id DESC LIMIT 1",
                userId)
        row.result == "FAILURE"
        row.detail.toString().contains("EMPTY_CART")
    }

    def "useSeparateShipping=trueかつshippingを別指定した場合は配送先住所に反映される"() {
        given:
        addToCart(ITEM_PLENTY, 1)
        def shippingJson = '''
            {"firstName":"Hanako","lastName":"Suzuki","address1":"2 Ship Ave","address2":null,
             "city":"Shipville","state":"NY","postalCode":"10001","country":"USA"}
        '''
        def body = """{"billing": ${BILLING_JSON}, "shipping": ${shippingJson}, "useSeparateShipping": true}"""

        when:
        def result = mockMvc.perform(post("/api/orders").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn()
        Long orderId = JsonPath.read(result.response.contentAsString, '$.orderId') as Long

        then:
        def order = jdbcTemplate.queryForMap("SELECT * FROM t_order WHERE order_id = ?", orderId)
        order.ship_to_first_name == "Hanako"
        order.ship_city == "Shipville"
        order.bill_to_first_name == "Taro"
        order.bill_city == "Testville"
    }
}
