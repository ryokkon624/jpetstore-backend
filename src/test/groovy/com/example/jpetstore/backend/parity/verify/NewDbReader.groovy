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

    // ------------------------------------------------------------------
    // #51 T1: アカウント系（W4/W5）
    // ------------------------------------------------------------------

    /** {@code m_account} の総行数。W4の{@code accountsCreated}（canonical）は本メソッドの前後差分で算出する。 */
    long accountCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM m_account", Long)
    }

    Long findUserIdByUsername(String username) {
        return jdbcTemplate.queryForObject("SELECT user_id FROM m_account WHERE username = ?", Long, username)
    }

    /**
     * canonicalのキー名（確定1の14項目）。ordinal位置で対応づける（{@link #accountRow}参照）。
     *
     * <p>{@code LegacyDbReader#accountRow}と同じ理由（列ラベルのcamelCaseがドライバ/DB実装依存で
     * 素通しされない場合がある）で、{@link #accountRow}はSELECT列順とこのリストの対応で直接
     * canonicalキーを組み立てる（{@code queryForMap}の列ラベル依存にしない・両側対称の防御的設計）。
     */
    private static final List<String> ACCOUNT_CANONICAL_KEYS = [
            "username", "email", "firstName", "lastName", "status", "address1", "address2", "city",
            "state", "postalCode", "country", "phone", "languagePreference", "favoriteCategoryId",
    ]

    /**
     * username -&gt; アカウント/プロフィールのcanonical14項目（{@code m_account JOIN m_profile}・確定1）。
     * 旧の{@code LegacyDbReader#accountRow}と対称（design §4「canonicalは両側ともDB直読みで組む」・SM-4）。
     */
    Map<String, String> accountRow(String username) {
        List<Map<String, String>> rows = jdbcTemplate.query(
                """
                SELECT A.username, A.email, A.first_name, A.last_name, A.status, A.address1,
                       A.address2, A.city, A.state, A.postal_code, A.country, A.phone,
                       P.language_preference, P.favorite_category_id
                  FROM m_account A JOIN m_profile P ON A.user_id = P.user_id
                 WHERE A.username = ?
                """,
                { rs, rowNum ->
                    Map<String, String> row = new LinkedHashMap<>()
                    ACCOUNT_CANONICAL_KEYS.eachWithIndex { String key, int idx ->
                        Object value = rs.getObject(idx + 1)
                        row[key] = value == null ? null : value.toString()
                    }
                    return row
                },
                username)
        return rows.isEmpty() ? null : rows[0]
    }

    String signonPasswordHash(long userId) {
        return jdbcTemplate.queryForObject("SELECT password_hash FROM m_signon WHERE user_id = ?", String, userId)
    }

    /**
     * W4/W5xの後始末（AC-neg2）。{@code t_register_attempt}/{@code t_login_attempt}も対象に含める
     * （ID-11のレート制限に再実行で引っかからないため。既存{@code OrderParitySpec}の作法を踏襲）。
     */
    void deleteAccountCascade(String username) {
        jdbcTemplate.update(
                "DELETE FROM t_audit_log WHERE actor_user_id IN (SELECT user_id FROM m_account WHERE username = ?)",
                username)
        jdbcTemplate.update(
                "DELETE FROM m_profile WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)",
                username)
        jdbcTemplate.update(
                "DELETE FROM m_signon WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)",
                username)
        jdbcTemplate.update("DELETE FROM t_login_attempt WHERE username = ?", username)
        jdbcTemplate.update("DELETE FROM m_account WHERE username = ?", username)
    }
}
