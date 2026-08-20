package com.example.jpetstore.backend.parity.verify

import org.springframework.jdbc.core.JdbcTemplate

/**
 * 新側（MySQL）からcanonical値を組む/リセットするための薄いラッパ（#48 AC9・AC10）。
 * {@code LegacyDbReader} と対になる（capture/verify両側とも「実行前ベースライン記録 → 駆動 →
 * 実行後差分算出 → 後始末（復元）」の同じ設計言語に揃える）。
 */
class NewDbReader {

    private final JdbcTemplate jdbcTemplate

    NewDbReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate
    }

    Integer inventoryQty(String itemId) {
        return jdbcTemplate.queryForObject("SELECT quantity FROM t_inventory WHERE item_id = ?", Integer, itemId)
    }

    void setInventoryQty(String itemId, int qty) {
        jdbcTemplate.update("UPDATE t_inventory SET quantity = ? WHERE item_id = ?", qty, itemId)
    }

    /** 本人の注文の最大order_id。1件も無ければnull。 */
    Long maxOrderId(long userId) {
        return jdbcTemplate.queryForObject("SELECT MAX(order_id) FROM t_order WHERE user_id = ?", Long, userId)
    }

    /**
     * 本人の注文件数。{@code ordersCreated}（canonical）は本メソッドの前後差分で算出する
     * （{@code order_id}はMySQLのAUTO_INCREMENTでテストスイート全体を跨いだグローバル採番のため、
     * {@link #maxOrderId}の差分は「新規作成件数」と一致しない＝{@code capture.LegacyDbReader}の
     * javadoc参照）。
     */
    long orderCount(long userId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_order WHERE user_id = ?", Long, userId)
    }

    Map<String, Object> orderRow(long orderId) {
        return jdbcTemplate.queryForMap("SELECT order_id, total_price FROM t_order WHERE order_id = ?", orderId)
    }

    List<Map<String, Object>> orderLines(long orderId) {
        return jdbcTemplate.queryForList(
                "SELECT item_id, quantity, unit_price FROM t_order_line WHERE order_id = ? ORDER BY line_num", orderId)
    }

    /** {@code baselineOrderId} より大きいIDの注文（本人分のみ）を削除する（後始末）。 */
    void deleteOrdersAbove(long userId, long baselineOrderId) {
        jdbcTemplate.update(
                "DELETE FROM t_order_line WHERE order_id IN " +
                        "(SELECT order_id FROM t_order WHERE user_id = ? AND order_id > ?)",
                userId, baselineOrderId)
        jdbcTemplate.update("DELETE FROM t_order WHERE user_id = ? AND order_id > ?", userId, baselineOrderId)
    }

    /** AC10: シナリオ実行前後で本人のカート残留を除去する（F4対策の新側担当分）。 */
    void clearCart(long userId) {
        jdbcTemplate.update(
                "DELETE FROM t_cart_item WHERE cart_id IN (SELECT cart_id FROM t_cart WHERE user_id = ?)", userId)
        jdbcTemplate.update("DELETE FROM t_cart WHERE user_id = ?", userId)
    }
}
