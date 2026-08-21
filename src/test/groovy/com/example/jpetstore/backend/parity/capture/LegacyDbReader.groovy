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

    /**
     * 在庫を復元/セットする。affected rowsを検査し、**0行なら例外でfailする**
     * （SM verification対応: itemId不一致・表名/列名変更等でUPDATEが黙って0行になり、
     * W3のID-1実証（在庫マイナス化）の前提が静かに崩れる経路を塞ぐ）。
     */
    void restoreInventoryQty(String itemId, int qty) {
        int affected = update("UPDATE INVENTORY SET QTY = ? WHERE ITEMID = ?", qty, itemId)
        if (affected != 1) {
            throw new IllegalStateException(
                    "restoreInventoryQty(itemId=${itemId}, qty=${qty})の更新行数が${affected}(期待値=1)。" +
                            "itemIdの不一致や表定義の変更等でUPDATEが対象行に当たっていない可能性がある。")
        }
    }

    long maxOrderId() {
        Object result = queryScalar("SELECT MAX(ORDERID) FROM ORDERS")
        return result == null ? 0L : (result as Number).longValue()
    }

    /**
     * 注文の総件数。{@code ordersCreated}（canonical）は本メソッドの前後差分で算出する
     * （{@link #maxOrderId()}の差分は採番機構がグローバルなため「件数」と一致しない場合がある。
     * legacy側はordernumシーケンスを毎回復元するため実害は無いが、新側〔MySQL AUTO_INCREMENT〕との
     * 対称性のためCOUNTベースに統一する）。
     */
    long orderCount() {
        return (queryScalar("SELECT COUNT(*) FROM ORDERS") as Number).longValue()
    }

    Map<String, Object> orderRow(long orderId) {
        return queryRow("SELECT ORDERID, TOTALPRICE FROM ORDERS WHERE ORDERID = ?", orderId)
    }

    /** {@code ORDERID}の存在有無（R7の一覧delta検証・R8bの前提「存在しないorderId」の検証に使う）。 */
    boolean orderExists(long orderId) {
        return (queryScalar("SELECT COUNT(*) FROM ORDERS WHERE ORDERID = ?", orderId) as Number).longValue() == 1
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

    /**
     * itemId -&gt; 商品名（{@code ITEM JOIN PRODUCT}）。R4（アイテム詳細）でJSPの表示テキストが
     * attribute群と連結されておりproductNameだけを正規表現で安全に切り出せないため、DB直読みで
     * 代替する（design.md §6-2「DB直読みで代替できる範囲はDBを優先する」）。
     */
    String productNameForItem(String itemId) {
        return queryScalar(
                "SELECT P.NAME FROM ITEM I JOIN PRODUCT P ON I.PRODUCTID = P.PRODUCTID WHERE I.ITEMID = ?",
                itemId) as String
    }

    long sequenceNextId(String name) {
        return (queryScalar("SELECT NEXTID FROM SEQUENCE WHERE NAME = ?", name) as Number).longValue()
    }

    void restoreSequenceNextId(String name, long value) {
        update("UPDATE SEQUENCE SET NEXTID = ? WHERE NAME = ?", value, name)
    }

    // ------------------------------------------------------------------
    // #51 T1: アカウント系（W4/W5）
    // ------------------------------------------------------------------

    /** {@code ACCOUNT} の総行数。W4の{@code accountsCreated}（canonical）は本メソッドの前後差分で算出する。 */
    long accountCount() {
        return (queryScalar("SELECT COUNT(*) FROM ACCOUNT") as Number).longValue()
    }

    /**
     * canonicalのキー名（確定1の14項目）。ordinal位置で対応づける（{@link #accountRow}参照）。
     *
     * <p>{@link #queryRow}/{@link #queryRows}は列ラベルを{@code toLowerCase()}するため、SQLの
     * {@code AS firstName}のようなcamelCaseエイリアスを素通しできない（HSQLDBが列ラベルを一旦
     * UPPERCASEへ正規化することとも相まって、そのまま使うと{@code firstname}になってしまい、
     * 新側{@code NewDbReader#accountRow}のcamelCaseキーと一致しなくなる＝実機で確認済み）。
     * このため{@link #accountRow}は{@link #queryRow}を使わず、SELECT列順とこのリストの対応で
     * 直接canonicalキーを組み立てる。
     */
    private static final List<String> ACCOUNT_CANONICAL_KEYS = [
            "username", "email", "firstName", "lastName", "status", "address1", "address2", "city",
            "state", "postalCode", "country", "phone", "languagePreference", "favoriteCategoryId",
    ]

    /**
     * username -&gt; アカウント/プロフィールのcanonical14項目（{@code ACCOUNT JOIN PROFILE}・確定1）。
     * {@code getAccountByUsername}（{@code Account.xml}）と異なり{@code BANNERDATA}へのJOINは行わない
     * （canonicalの比較対象に{@code bannerName}は含まないため。存在確認は{@link #bannerDataHasCategory}で別途行う）。
     */
    Map<String, String> accountRow(String username) {
        PreparedStatement ps = connection.prepareStatement(
                """
                SELECT A.USERID, A.EMAIL, A.FIRSTNAME, A.LASTNAME, A.STATUS, A.ADDR1, A.ADDR2, A.CITY,
                       A.STATE, A.ZIP, A.COUNTRY, A.PHONE, P.LANGPREF, P.FAVCATEGORY
                  FROM ACCOUNT A JOIN PROFILE P ON A.USERID = P.USERID
                 WHERE A.USERID = ?
                """)
        try {
            ps.setString(1, username)
            def rs = ps.executeQuery()
            try {
                if (!rs.next()) {
                    return null
                }
                Map<String, String> row = new LinkedHashMap<>()
                ACCOUNT_CANONICAL_KEYS.eachWithIndex { String key, int idx ->
                    Object value = rs.getObject(idx + 1)
                    row[key] = value == null ? null : value.toString()
                }
                return row
            } finally {
                rs.close()
            }
        } finally {
            ps.close()
        }
    }

    /** {@code BANNERDATA} に指定カテゴリの行が存在するか（W4前提: {@code getAccountByUsername}のINNER JOIN依存）。 */
    boolean bannerDataHasCategory(String categoryId) {
        long count = (queryScalar("SELECT COUNT(*) FROM BANNERDATA WHERE FAVCATEGORY = ?", categoryId) as Number).longValue()
        return count == 1
    }

    /** {@code SIGNON.PASSWORD}（平文）。W5x前提の「編集前後で不変/一致」確認に使う。 */
    String signonPassword(String username) {
        return queryScalar("SELECT PASSWORD FROM SIGNON WHERE USERNAME = ?", username) as String
    }

    /**
     * W4/W5xの後始末（AC-neg2）。{@code signon -&gt; profile -&gt; account}の順にDELETEし、各段でaffected rowsを
     * 検査する（0行ならfail。SM verification対応と同じ規律＝#48/#49の教訓）。
     */
    void deleteAccountCascade(String username) {
        int signonDeleted = update("DELETE FROM SIGNON WHERE USERNAME = ?", username)
        if (signonDeleted != 1) {
            throw new IllegalStateException(
                    "deleteAccountCascade(username=${username}): SIGNON削除行数が${signonDeleted}(期待値=1)")
        }
        int profileDeleted = update("DELETE FROM PROFILE WHERE USERID = ?", username)
        if (profileDeleted != 1) {
            throw new IllegalStateException(
                    "deleteAccountCascade(username=${username}): PROFILE削除行数が${profileDeleted}(期待値=1)")
        }
        int accountDeleted = update("DELETE FROM ACCOUNT WHERE USERID = ?", username)
        if (accountDeleted != 1) {
            throw new IllegalStateException(
                    "deleteAccountCascade(username=${username}): ACCOUNT削除行数が${accountDeleted}(期待値=1)")
        }
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

    private int update(String sql, Object... params) {
        PreparedStatement ps = connection.prepareStatement(sql)
        try {
            bind(ps, params)
            return ps.executeUpdate()
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
