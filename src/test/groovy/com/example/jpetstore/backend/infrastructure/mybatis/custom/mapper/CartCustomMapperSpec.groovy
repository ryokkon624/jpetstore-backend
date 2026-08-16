package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper

import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartHeaderCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartItemWriteCustomEntity
import com.example.jpetstore.backend.support.IntegrationTestBase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Tag

/**
 * #4: カート（t_cart/t_cart_item）の永続化カスタムXMLマッパーを検証する。
 * AC2/ID-17（単一表+UNIQUE(cart_id,item_id)で幽霊行を構造的に排除）・D3（last-write-win upsert）を実証する。
 */
@Tag("integration")
class CartCustomMapperSpec extends IntegrationTestBase {

    private static final String USERNAME = "cart_mapper_test_user"

    @Autowired
    CartCustomMapper mapper

    @Autowired
    JdbcTemplate jdbcTemplate

    Long userId

    void setup() {
        jdbcTemplate.update("DELETE FROM t_cart_item WHERE cart_id IN (SELECT cart_id FROM t_cart WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?))", USERNAME)
        jdbcTemplate.update("DELETE FROM t_cart WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)", USERNAME)
        jdbcTemplate.update("DELETE FROM m_account WHERE username = ?", USERNAME)
        jdbcTemplate.update(
                """
                INSERT INTO m_account
                    (username, email, first_name, last_name, status, address1, city, state, postal_code, country, phone,
                     create_program, update_program)
                VALUES (?, ?, 'Cart', 'MapperTestUser', 'OK', '1 Test St', 'Testville', 'CA', '90000', 'USA', '555-0100',
                     'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                USERNAME, "${USERNAME}@example.com")
        userId = jdbcTemplate.queryForObject("SELECT user_id FROM m_account WHERE username = ?", Long, USERNAME)
    }

    private Long ensureCart() {
        def header = new CartHeaderCustomEntity()
        header.userId = userId
        mapper.ensureCart(header)
        header.cartId
    }

    def "ensureCartは初回呼び出しでカートを作成しcartIdを補完する"() {
        given:
        def header = new CartHeaderCustomEntity()
        header.userId = userId

        when:
        mapper.ensureCart(header)

        then:
        header.cartId != null

        and: "DBにも1件だけ作成される"
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_cart WHERE user_id = ?", Integer, userId) == 1
    }

    def "ensureCartは2回目以降呼び出しても同じcartIdを返す(1ユーザ1カート)"() {
        given:
        def first = new CartHeaderCustomEntity()
        first.userId = userId
        mapper.ensureCart(first)

        when:
        def second = new CartHeaderCustomEntity()
        second.userId = userId
        mapper.ensureCart(second)

        then:
        second.cartId == first.cartId
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_cart WHERE user_id = ?", Integer, userId) == 1
    }

    def "selectCartItemsは空カートで空リストを返す"() {
        given:
        def cartId = ensureCart()

        expect:
        mapper.selectCartItems(cartId).isEmpty()
    }

    def "upsertCartItemQuantityは新規行を作成し、selectCartItemsがJOINでproductName/attribute1/listPrice/在庫qtyを返す"() {
        given:
        def cartId = ensureCart()
        def write = new CartItemWriteCustomEntity()
        write.cartId = cartId
        write.itemId = "EST-1"
        write.quantity = 2

        when:
        mapper.upsertCartItemQuantity(write)
        def items = mapper.selectCartItems(cartId)

        then:
        items.size() == 1
        items[0].itemId == "EST-1"
        items[0].productId == "FI-SW-01"
        items[0].productName == "Angelfish"
        items[0].attribute1 == "Large"
        items[0].listPrice == 16.50
        items[0].quantity == 2
        items[0].stockQuantity == 100
    }

    def "AC2/ID-17: upsertCartItemQuantityは同一(cart_id,item_id)を重複行にせず絶対値で上書きする(幽霊行を作らない)"() {
        given:
        def cartId = ensureCart()
        def first = new CartItemWriteCustomEntity()
        first.cartId = cartId
        first.itemId = "EST-1"
        first.quantity = 2
        mapper.upsertCartItemQuantity(first)

        when: "同一アイテムを別の絶対値で再度upsertする"
        def second = new CartItemWriteCustomEntity()
        second.cartId = cartId
        second.itemId = "EST-1"
        second.quantity = 5
        mapper.upsertCartItemQuantity(second)
        def items = mapper.selectCartItems(cartId)

        then: "行は1件のまま、数量は最後の絶対値で上書きされる(last-write-win・D3)"
        items.size() == 1
        items[0].quantity == 5
        jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_cart_item WHERE cart_id = ? AND item_id = ?", Integer, cartId, "EST-1") == 1
    }

    def "selectItemForCartは存在するアイテムの在庫qtyと、当該カート内の既存数量(未追加なら0)を返す"() {
        given:
        def cartId = ensureCart()

        expect: "未追加のアイテムはcurrentQuantity=0"
        with(mapper.selectItemForCart("EST-2", cartId)) {
            itemId == "EST-2"
            stockQuantity == 1
            currentQuantity == 0
        }

        when: "カートに追加した後は既存数量を反映する"
        def write = new CartItemWriteCustomEntity()
        write.cartId = cartId
        write.itemId = "EST-2"
        write.quantity = 1
        mapper.upsertCartItemQuantity(write)

        then:
        with(mapper.selectItemForCart("EST-2", cartId)) {
            currentQuantity == 1
            stockQuantity == 1
        }
    }

    def "selectItemForCartは在庫切れアイテム(EST-3, qty=0)もstockQuantity=0で返す(アイテム自体は存在)"() {
        given:
        def cartId = ensureCart()

        expect:
        with(mapper.selectItemForCart("EST-3", cartId)) {
            itemId == "EST-3"
            stockQuantity == 0
            currentQuantity == 0
        }
    }

    def "AC4/SBD-10: selectItemForCartは存在しないitemIdでnullを返す"() {
        given:
        def cartId = ensureCart()

        expect:
        mapper.selectItemForCart("NOPE", cartId) == null
    }

    def "AC2: deleteCartItemは行を削除する(数量0以下相当の単一削除経路)"() {
        given:
        def cartId = ensureCart()
        def write = new CartItemWriteCustomEntity()
        write.cartId = cartId
        write.itemId = "EST-1"
        write.quantity = 3
        mapper.upsertCartItemQuantity(write)

        when:
        mapper.deleteCartItem(cartId, "EST-1")

        then:
        mapper.selectCartItems(cartId).isEmpty()
        jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_cart_item WHERE cart_id = ? AND item_id = ?", Integer, cartId, "EST-1") == 0
    }
}
