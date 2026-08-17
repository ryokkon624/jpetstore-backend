package com.example.jpetstore.backend.presentation.rest

import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.infrastructure.security.AuthCookieSupport
import com.example.jpetstore.backend.infrastructure.security.JwtService
import com.example.jpetstore.backend.support.IntegrationTestBase
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Tag

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * #8 AC-neg2(過剰販売防止・arch §4.1): 在庫qty=1の同一アイテムへ2ユーザーが同時に注文確定を試みても、
 * 片方のみ成功しもう片方は409(在庫不足)になり、在庫が負数化しない(売り越し不可)ことを実DBで実証する。
 * ガード付きUPDATEの行ロックによる直列化を、実際の並行リクエストで固定する
 * （{@code OptimisticLockSqlSemanticsSpec}のSQL意味論実証を、注文確定フロー全体の並行安全性へ拡張する）。
 */
@Tag("integration")
@AutoConfigureMockMvc
class OrderConcurrencyIntegrationSpec extends IntegrationTestBase {

    private static final String USERNAME_A = "order_concurrency_test_user_a"
    private static final String USERNAME_B = "order_concurrency_test_user_b"
    private static final String CONTESTED_ITEM = "ZZ-ORD-C1"

    private static final String BILLING_JSON = '''
        {"firstName":"Taro","lastName":"Yamada","address1":"1 Test St","address2":null,
         "city":"Testville","state":"CA","postalCode":"90000","country":"USA"}
    '''
    private static final String ORDER_BODY = """{"billing": ${BILLING_JSON}, "useSeparateShipping": false}"""

    @Autowired
    MockMvc mockMvc

    @Autowired
    JwtService jwtService

    @Autowired
    JdbcTemplate jdbcTemplate

    void setup() {
        cleanupFixtures()
        jdbcTemplate.update(
                """
                INSERT INTO m_item (item_id, product_id, list_price, status, attribute1, create_program, update_program)
                VALUES (?, 'FI-SW-01', 12.34, 'P', 'Test', 'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                CONTESTED_ITEM)
        jdbcTemplate.update(
                "INSERT INTO t_inventory (item_id, quantity, create_program, update_program) VALUES (?, 1, 'TEST_FIXTURE', 'TEST_FIXTURE')",
                CONTESTED_ITEM)
    }

    void cleanup() {
        cleanupFixtures()
    }

    private void cleanupFixtures() {
        jdbcTemplate.update("DELETE FROM t_audit_log WHERE action = 'ORDER_CREATE'")
        jdbcTemplate.update("DELETE FROM t_order_line WHERE item_id = ?", CONTESTED_ITEM)
        jdbcTemplate.update("DELETE FROM t_order WHERE user_id IN (SELECT user_id FROM m_account WHERE username IN (?, ?))", USERNAME_A, USERNAME_B)
        jdbcTemplate.update("DELETE FROM t_cart_item WHERE cart_id IN (SELECT cart_id FROM t_cart WHERE user_id IN (SELECT user_id FROM m_account WHERE username IN (?, ?)))", USERNAME_A, USERNAME_B)
        jdbcTemplate.update("DELETE FROM t_cart WHERE user_id IN (SELECT user_id FROM m_account WHERE username IN (?, ?))", USERNAME_A, USERNAME_B)
        jdbcTemplate.update("DELETE FROM m_account WHERE username IN (?, ?)", USERNAME_A, USERNAME_B)
        jdbcTemplate.update("DELETE FROM t_inventory WHERE item_id = ?", CONTESTED_ITEM)
        jdbcTemplate.update("DELETE FROM m_item WHERE item_id = ?", CONTESTED_ITEM)
    }

    private Cookie createUserAndCookie(String username) {
        jdbcTemplate.update(
                """
                INSERT INTO m_account
                    (username, email, first_name, last_name, status, address1, city, state, postal_code, country, phone,
                     create_program, update_program)
                VALUES (?, ?, 'Order', 'ConcurrencyTestUser', 'OK', '1 Test St', 'Testville', 'CA', '90000', 'USA', '555-0100',
                     'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                username, "${username}@example.com")
        Long userId = jdbcTemplate.queryForObject("SELECT user_id FROM m_account WHERE username = ?", Long, username)
        def user = new AuthenticatedUser(userId, username, ["USER"])
        new Cookie(AuthCookieSupport.ACCESS_TOKEN_COOKIE, jwtService.generateAccessToken(user))
    }

    private void addToCart(Cookie cookie, String itemId, int quantity) {
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"itemId":"${itemId}","quantity":${quantity}}"""))
    }

    def "在庫qty=1の同一アイテムへ2ユーザーが同時にplaceOrderすると1件成功・1件409になり在庫は負数化しない"() {
        given: "2ユーザーがそれぞれ自分のカートに同一アイテムを1個ずつ入れている(cart-add自体は在庫を減らさないため両方成功する)"
        def cookieA = createUserAndCookie(USERNAME_A)
        def cookieB = createUserAndCookie(USERNAME_B)
        addToCart(cookieA, CONTESTED_ITEM, 1)
        addToCart(cookieB, CONTESTED_ITEM, 1)

        and: "2スレッドが同時にplaceOrderを叩けるようCountDownLatchで同期する"
        ExecutorService executor = Executors.newFixedThreadPool(2)
        CountDownLatch readyLatch = new CountDownLatch(2)
        CountDownLatch startLatch = new CountDownLatch(1)

        when:
        def futureA = executor.submit({
            readyLatch.countDown()
            startLatch.await()
            mockMvc.perform(post("/api/orders").with(csrf()).cookie(cookieA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(ORDER_BODY)).andReturn().response.status
        } as java.util.concurrent.Callable<Integer>)
        def futureB = executor.submit({
            readyLatch.countDown()
            startLatch.await()
            mockMvc.perform(post("/api/orders").with(csrf()).cookie(cookieB)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(ORDER_BODY)).andReturn().response.status
        } as java.util.concurrent.Callable<Integer>)

        readyLatch.await(5, TimeUnit.SECONDS)
        startLatch.countDown()
        int statusA = futureA.get(15, TimeUnit.SECONDS)
        int statusB = futureB.get(15, TimeUnit.SECONDS)
        executor.shutdown()

        then: "片方のみ201(成功)・もう片方は409(在庫不足)"
        def statuses = [statusA, statusB]
        statuses.count { it == 201 } == 1
        statuses.count { it == 409 } == 1

        and: "在庫は0のまま(負数化しない・売り越し不可)"
        jdbcTemplate.queryForObject("SELECT quantity FROM t_inventory WHERE item_id = ?", Integer, CONTESTED_ITEM) == 0

        and: "注文は1件のみ成立する(t_order_lineも1行のみ)"
        jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_order_line WHERE item_id = ?", Integer, CONTESTED_ITEM) == 1
        jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_order o JOIN m_account a ON a.user_id = o.user_id WHERE a.username IN (?, ?)",
                Integer, USERNAME_A, USERNAME_B) == 1
    }
}
