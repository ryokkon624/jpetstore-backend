package com.example.jpetstore.backend.presentation.rest

import com.example.jpetstore.backend.domain.order.OrderRepository
import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.infrastructure.security.AuthCookieSupport
import com.example.jpetstore.backend.infrastructure.security.JwtService
import com.example.jpetstore.backend.support.IntegrationTestBase
import jakarta.servlet.http.Cookie
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Tag

import static org.mockito.ArgumentMatchers.any
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * #40 N3/AC1/AC-neg3: L3 N3（在庫不足以外の失敗が監査ゼロのまま伝播＝ID-22未達）のライブPoCを名指しで
 * 固定する回帰テスト。出典: {@code reports/after/l3-security-regression-backend.md} §2.1 N3
 * （SEC・run {@code security/20260819_01}）。
 *
 * <p>#40 AC2/AC3（{@code @Size}/{@code @Valid}）導入後、入力検証由来の500経路（N11）はBean Validationで
 * 400に塞がれるため、本specでは「在庫不足以外の想定外の失敗」を {@link OrderRepository} への
 * {@link MockitoSpyBean} で直接再現し、修正前は監査ゼロのまま伝播していた経路に
 * {@code ORDER_CREATE}/{@code FAILURE} 監査行が必ず残ることを実証する。
 */
@Tag("integration")
@AutoConfigureMockMvc
class OrderFailureAuditL3RegressionSpec extends IntegrationTestBase {

    private static final String USERNAME = "order_failure_audit_test_user"
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

    @MockitoSpyBean
    OrderRepository orderRepository

    Long userId
    Cookie accessTokenCookie

    void setup() {
        cleanupFixtures()
        jdbcTemplate.update(
                """
                INSERT INTO m_account
                    (username, email, first_name, last_name, status, address1, city, state, postal_code, country, phone,
                     create_program, update_program)
                VALUES (?, ?, 'Order', 'FailureAuditTestUser', 'OK', '1 Test St', 'Testville', 'CA', '90000', 'USA', '555-0100',
                     'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                USERNAME, "${USERNAME}@example.com")
        userId = jdbcTemplate.queryForObject("SELECT user_id FROM m_account WHERE username = ?", Long, USERNAME)
        def user = new AuthenticatedUser(userId, USERNAME, ["USER"])
        accessTokenCookie = new Cookie(AuthCookieSupport.ACCESS_TOKEN_COOKIE, jwtService.generateAccessToken(user))
    }

    void cleanup() {
        Mockito.reset(orderRepository)
        cleanupFixtures()
    }

    private void cleanupFixtures() {
        jdbcTemplate.update("DELETE FROM t_audit_log WHERE action = 'ORDER_CREATE'")
        jdbcTemplate.update(
                "DELETE FROM t_cart_item WHERE cart_id IN (SELECT cart_id FROM t_cart WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?))",
                USERNAME)
        jdbcTemplate.update(
                "DELETE FROM t_cart WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)", USERNAME)
        jdbcTemplate.update("DELETE FROM m_account WHERE username = ?", USERNAME)
    }

    private void addToCart(String itemId, int quantity) {
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"itemId":"${itemId}","quantity":${quantity}}"""))
                .andExpect(status().isOk())
    }

    def "L3 N3 AC-neg3: 在庫不足以外の想定外の失敗でもORDER_CREATE/FAILURE監査行が1件残る(修正前は監査ゼロのまま伝播していた)"() {
        given: "既存カタログseedのEST-1をカートに追加(insertHeaderで即座に失敗するため在庫減算は発生しない)"
        addToCart("EST-1", 1)
        Mockito.doThrow(new RuntimeException("simulated unexpected failure"))
                .when(orderRepository).insertHeader(any())

        when:
        mockMvc.perform(post("/api/orders").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"billing": ${BILLING_JSON}, "useSeparateShipping": false}"""))

        then: "失敗detailにDB由来の生メッセージ・例外詳細は含めない(SBD-10)"
        def row = jdbcTemplate.queryForMap(
                "SELECT * FROM t_audit_log WHERE action = 'ORDER_CREATE' AND actor_user_id = ? ORDER BY audit_id DESC LIMIT 1",
                userId)
        row.result == "FAILURE"
        row.detail.toString().contains("UNEXPECTED_ERROR")
        !row.detail.toString().contains("simulated unexpected failure")
    }
}
