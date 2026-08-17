package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper

import com.example.jpetstore.backend.support.IntegrationTestBase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Tag

/**
 * #9/#10: 注文の読取専用カスタムXMLマッパー（一覧・件数・ヘッダ・明細JOIN）を検証する。
 *
 * <p>注文seedは存在しないため、setupで隔離フィクスチャ（ZZ-*のaccount/item/order）をJDBCで直接投入し、
 * cleanupで削除する（Sprint11で確立した隔離方針＝{@code OrderConcurrencyIntegrationSpec}と同型）。
 */
@Tag("integration")
class OrderCustomMapperSpec extends IntegrationTestBase {

    private static final String USERNAME = "order_mapper_test_user"
    private static final String OTHER_USERNAME = "order_mapper_other_user"
    private static final String ITEM_ID = "ZZ-OM-1"

    @Autowired
    OrderCustomMapper mapper

    @Autowired
    JdbcTemplate jdbcTemplate

    Long userId
    Long otherUserId
    List<Long> orderIds = []

    void setup() {
        cleanupFixtures()
        userId = createAccount(USERNAME)
        otherUserId = createAccount(OTHER_USERNAME)
        insertItem(ITEM_ID)

        // userIdに5件の注文(orderId昇順)を作る。order_dateは全て同日でもorder_id DESCで決定的にソートされることを検証する。
        (1..5).each { i ->
            orderIds << insertOrder(userId, LocalDate2026(i), new BigDecimal("10.00").multiply(BigDecimal.valueOf(i)))
        }
        // otherUserIdの注文は一覧・件数のスコープに含まれないことを確認するためのノイズ。
        insertOrder(otherUserId, LocalDate2026(1), new BigDecimal("99.99"))
    }

    void cleanup() {
        cleanupFixtures()
    }

    private static java.time.LocalDate LocalDate2026(int day) {
        java.time.LocalDate.of(2026, 1, day)
    }

    private void cleanupFixtures() {
        jdbcTemplate.update("DELETE FROM t_order_line WHERE item_id = ?", ITEM_ID)
        jdbcTemplate.update("DELETE FROM t_order WHERE user_id IN (SELECT user_id FROM m_account WHERE username IN (?, ?))", USERNAME, OTHER_USERNAME)
        jdbcTemplate.update("DELETE FROM m_account WHERE username IN (?, ?)", USERNAME, OTHER_USERNAME)
        jdbcTemplate.update("DELETE FROM m_item WHERE item_id = ?", ITEM_ID)
    }

    private Long createAccount(String username) {
        jdbcTemplate.update(
                """
                INSERT INTO m_account
                    (username, email, first_name, last_name, status, address1, city, state, postal_code, country, phone,
                     create_program, update_program)
                VALUES (?, ?, 'Order', 'MapperTestUser', 'OK', '1 Test St', 'Testville', 'CA', '90000', 'USA', '555-0100',
                     'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                username, "${username}@example.com")
        jdbcTemplate.queryForObject("SELECT user_id FROM m_account WHERE username = ?", Long, username)
    }

    private void insertItem(String itemId) {
        jdbcTemplate.update(
                """
                INSERT INTO m_item (item_id, product_id, list_price, unit_cost, status, attribute1,
                    create_program, update_program)
                VALUES (?, 'FI-SW-01', 10.00, 10.00, 'P', 'Test', 'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                itemId)
    }

    private Long insertOrder(Long ownerUserId, java.time.LocalDate orderDate, BigDecimal totalPrice) {
        jdbcTemplate.update(
                """
                INSERT INTO t_order (
                    user_id, order_date,
                    ship_address1, ship_city, ship_state, ship_postal_code, ship_country,
                    bill_address1, bill_city, bill_state, bill_postal_code, bill_country,
                    total_price, bill_to_first_name, bill_to_last_name, ship_to_first_name, ship_to_last_name,
                    status_code, create_program, update_program
                ) VALUES (
                    ?, ?,
                    '1 Test St', 'Testville', 'CA', '90000', 'USA',
                    '1 Test St', 'Testville', 'CA', '90000', 'USA',
                    ?, 'Taro', 'Yamada', 'Taro', 'Yamada',
                    'NEW', 'TEST_FIXTURE', 'TEST_FIXTURE'
                )
                """,
                ownerUserId, orderDate, totalPrice)
        Long orderId = jdbcTemplate.queryForObject(
                "SELECT order_id FROM t_order WHERE user_id = ? ORDER BY order_id DESC LIMIT 1", Long, ownerUserId)
        jdbcTemplate.update(
                """
                INSERT INTO t_order_line (order_id, line_num, item_id, quantity, unit_price, create_program, update_program)
                VALUES (?, 1, ?, 1, 10.00, 'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                orderId, ITEM_ID)
        orderId
    }

    def "selectOrdersByUserIdは本人スコープでorder_id DESC(新しい順)に返す"() {
        given:
        def rows = mapper.selectOrdersByUserId(userId, 0, 10)

        expect:
        rows.size() == 5
        rows*.orderId == orderIds.reverse()
    }

    def "selectOrdersByUserIdはLIMIT/OFFSETでページングできる(size=2で3頁)"() {
        given:
        def page1 = mapper.selectOrdersByUserId(userId, 0, 2)
        def page2 = mapper.selectOrdersByUserId(userId, 2, 2)
        def page3 = mapper.selectOrdersByUserId(userId, 4, 2)

        expect:
        page1.size() == 2
        page2.size() == 2
        page3.size() == 1
        (page1 + page2 + page3)*.orderId == orderIds.reverse()
    }

    def "countOrdersByUserIdは本人スコープの件数のみを返す(他ユーザーの注文を含めない)"() {
        expect:
        mapper.countOrdersByUserId(userId) == 5
        mapper.countOrdersByUserId(otherUserId) == 1
    }

    def "selectOrderHeaderByIdはuserIdを含む単一行を返し、存在しなければnullを返す"() {
        given:
        def header = mapper.selectOrderHeaderById(orderIds[0])

        expect:
        header.orderId == orderIds[0]
        header.userId == userId
        header.totalPrice == new BigDecimal("10.00")

        and:
        mapper.selectOrderHeaderById(-1L) == null
    }

    def "selectOrderLinesByOrderIdはJOINでproduct_nameを補完しline_num順に返す"() {
        given:
        def lines = mapper.selectOrderLinesByOrderId(orderIds[0])

        expect:
        lines.size() == 1
        lines[0].itemId == ITEM_ID
        lines[0].productName == "Angelfish"
        lines[0].quantity == 1
        lines[0].unitPrice == new BigDecimal("10.00")
    }
}
