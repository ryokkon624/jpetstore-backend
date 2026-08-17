package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper

import com.example.jpetstore.backend.domain.concurrency.AffectedRows
import com.example.jpetstore.backend.domain.exception.InsufficientStockException
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.InventoryDecreaseCustomEntity
import com.example.jpetstore.backend.support.IntegrationTestBase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Tag

/**
 * #8 AC2(arch §4.1): 在庫のガード付きアトミック減算SQLの意味論を実DBで固定する。
 * qty>=n→affected rows=1&減算／qty<n→affected rows=0&在庫不変、をそれぞれ実証する
 * （{@code OptimisticLockSqlSemanticsSpec} が手本）。
 */
@Tag("integration")
class InventoryCustomMapperSpec extends IntegrationTestBase {

    private static final String ITEM_ID = "ZZ-INV-1"

    @Autowired
    InventoryCustomMapper mapper

    @Autowired
    JdbcTemplate jdbcTemplate

    void setup() {
        jdbcTemplate.update("DELETE FROM t_order_line WHERE item_id = ?", ITEM_ID)
        jdbcTemplate.update("DELETE FROM t_inventory WHERE item_id = ?", ITEM_ID)
        jdbcTemplate.update("DELETE FROM m_item WHERE item_id = ?", ITEM_ID)
        jdbcTemplate.update(
                """
                INSERT INTO m_item (item_id, product_id, list_price, status, attribute1, create_program, update_program)
                VALUES (?, 'FI-SW-01', 9.99, 'P', 'Test', 'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                ITEM_ID)
        jdbcTemplate.update(
                "INSERT INTO t_inventory (item_id, quantity, create_program, update_program) VALUES (?, 3, 'TEST_FIXTURE', 'TEST_FIXTURE')",
                ITEM_ID)
    }

    void cleanup() {
        jdbcTemplate.update("DELETE FROM t_inventory WHERE item_id = ?", ITEM_ID)
        jdbcTemplate.update("DELETE FROM m_item WHERE item_id = ?", ITEM_ID)
    }

    private static InventoryDecreaseCustomEntity decrease(String itemId, int quantity) {
        def e = new InventoryDecreaseCustomEntity()
        e.itemId = itemId
        e.quantity = quantity
        e.updateUserId = 1L
        e
    }

    def "在庫充足(quantity>=n)はaffected rows=1で在庫がnだけ減算される"() {
        when:
        int rows = mapper.decreaseInventory(decrease(ITEM_ID, 2))

        then:
        rows == 1
        AffectedRows.requireUpdated(rows, { new InsufficientStockException(ITEM_ID) }) // 例外を投げない
        jdbcTemplate.queryForObject("SELECT quantity FROM t_inventory WHERE item_id = ?", Integer, ITEM_ID) == 1
    }

    def "在庫不足(quantity<n)はaffected rows=0で在庫が不変のままInsufficientStockExceptionになる"() {
        when:
        int rows = mapper.decreaseInventory(decrease(ITEM_ID, 5))

        then:
        rows == 0
        jdbcTemplate.queryForObject("SELECT quantity FROM t_inventory WHERE item_id = ?", Integer, ITEM_ID) == 3

        when:
        AffectedRows.requireUpdated(rows, { new InsufficientStockException(ITEM_ID) })

        then:
        def e = thrown(InsufficientStockException)
        e.itemId() == ITEM_ID
    }

    def "ちょうど在庫数と同数の減算はaffected rows=1で在庫が0になる(境界値)"() {
        when:
        int rows = mapper.decreaseInventory(decrease(ITEM_ID, 3))

        then:
        rows == 1
        jdbcTemplate.queryForObject("SELECT quantity FROM t_inventory WHERE item_id = ?", Integer, ITEM_ID) == 0
    }
}
