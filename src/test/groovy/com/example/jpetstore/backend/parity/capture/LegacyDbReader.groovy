package com.example.jpetstore.backend.parity.capture

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement

/**
 * legacy HSQLDB(9002)への直結読み出し／後始末用の復元（#48 AC6・design.md §7.1）。
 *
 * <p>{@code java.sql.*} のみ参照し、HSQLDBのクラスをコンパイル時参照しない（Q2確定: ドライバは
 * capture専用の {@code parityLegacyJdbc} configurationにのみ置き、{@code testImplementation}の
 * classpathへは入れない。既存IT26本のclasspathを変えないため）。
 *
 * <p>テーブル定義は実機で確認済み（{@code INVENTORY(ITEMID, QTY)}・
 * {@code ORDERS(ORDERID, ..., TOTALPRICE, ...)}・{@code LINEITEM(ORDERID, LINENUM, ITEMID, QUANTITY, UNITPRICE)}・
 * {@code ORDERSTATUS(ORDERID, LINENUM, TIMESTAMP, STATUS)}・{@code SEQUENCE(NAME, NEXTID)}）。
 */
class LegacyDbReader implements Closeable {

    private final Connection connection

    LegacyDbReader(String jdbcUrl, String user, String password, String driverClassName) {
        Class.forName(driverClassName)
        connection = DriverManager.getConnection(jdbcUrl, user, password)
    }

    int inventoryQty(String itemId) {
        return (queryScalar("SELECT QTY FROM INVENTORY WHERE ITEMID = ?", itemId) as Number).intValue()
    }

    void restoreInventoryQty(String itemId, int qty) {
        update("UPDATE INVENTORY SET QTY = ? WHERE ITEMID = ?", qty, itemId)
    }

    long maxOrderId() {
        Object result = queryScalar("SELECT MAX(ORDERID) FROM ORDERS")
        return result == null ? 0L : (result as Number).longValue()
    }

    Map<String, Object> orderRow(long orderId) {
        return queryRow("SELECT ORDERID, TOTALPRICE FROM ORDERS WHERE ORDERID = ?", orderId)
    }

    List<Map<String, Object>> orderLines(long orderId) {
        return queryRows(
                "SELECT ITEMID, QUANTITY, UNITPRICE FROM LINEITEM WHERE ORDERID = ? ORDER BY LINENUM", orderId)
    }

    /** 採取で増えた注文を後始末する（AC-neg2・D2）。{@code baselineOrderId} より大きいIDを全削除する。 */
    void deleteOrdersAbove(long baselineOrderId) {
        update("DELETE FROM LINEITEM WHERE ORDERID > ?", baselineOrderId)
        update("DELETE FROM ORDERSTATUS WHERE ORDERID > ?", baselineOrderId)
        update("DELETE FROM ORDERS WHERE ORDERID > ?", baselineOrderId)
    }

    long sequenceNextId(String name) {
        return (queryScalar("SELECT NEXTID FROM SEQUENCE WHERE NAME = ?", name) as Number).longValue()
    }

    void restoreSequenceNextId(String name, long value) {
        update("UPDATE SEQUENCE SET NEXTID = ? WHERE NAME = ?", value, name)
    }

    private Object queryScalar(String sql, Object... params) {
        PreparedStatement ps = connection.prepareStatement(sql)
        try {
            bind(ps, params)
            def rs = ps.executeQuery()
            try {
                return rs.next() ? rs.getObject(1) : null
            } finally {
                rs.close()
            }
        } finally {
            ps.close()
        }
    }

    private Map<String, Object> queryRow(String sql, Object... params) {
        List<Map<String, Object>> rows = queryRows(sql, params)
        return rows.isEmpty() ? null : rows[0]
    }

    private List<Map<String, Object>> queryRows(String sql, Object... params) {
        List<Map<String, Object>> rows = []
        PreparedStatement ps = connection.prepareStatement(sql)
        try {
            bind(ps, params)
            def rs = ps.executeQuery()
            try {
                def meta = rs.getMetaData()
                while (rs.next()) {
                    Map<String, Object> row = [:]
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row[meta.getColumnName(i).toLowerCase()] = rs.getObject(i)
                    }
                    rows << row
                }
            } finally {
                rs.close()
            }
        } finally {
            ps.close()
        }
        return rows
    }

    private void update(String sql, Object... params) {
        PreparedStatement ps = connection.prepareStatement(sql)
        try {
            bind(ps, params)
            ps.executeUpdate()
        } finally {
            ps.close()
        }
    }

    private static void bind(PreparedStatement ps, Object... params) {
        params.eachWithIndex { p, idx -> ps.setObject((idx as int) + 1, p) }
    }

    @Override
    void close() {
        connection.close()
    }
}
