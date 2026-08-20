package com.example.jpetstore.backend.parity.canonical

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * L2パリティ検証の canonical（正規形）スナップショット（#48 AC1・design.md §1）。
 *
 * <p>旧（Struts/.do/JSP/HSQLDB）・新（REST/SPA/MySQL）どちらの語彙でもない第三の正規形。
 * 読み取り系・状態変更系を単一の型で表現し、シナリオが使わないフィールドは null／空のままにする
 * （#49がモデル自体を触らずにシナリオを横展開できるようにするため）。
 *
 * <p>比較から除外するフィールド（design.md §1.2）はそもそも本モデルに持たせない
 * （新側WHO 6列・version・自動採番ID・created_at/updated_at／旧側creditcard/exprdate/cardtype・
 * courier/locale・orderstatus・linenum）。
 */
class ParitySnapshot {

    /** SUCCESS/FAILURE。write系シナリオのみ使用する（read系はnullのまま）。 */
    String outcome

    /** itemId -&gt; 在庫増減（デルタ）。シナリオ実行前後の差分のみを保持する（AC2・F1・絶対値比較はしない）。 */
    Map<String, Integer> inventoryDelta = [:]

    /** 注文の増加件数。orderid/order_id自体の値は比較しない（採番機構が違う＝ID-23）。 */
    Integer ordersCreated

    /** 合計金額。比較前に {@link #normalize()} でscale(2)の文字列へ揃える（BigDecimal相当の比較・誤差0）。 */
    String orderTotal

    /** 明細（itemId, quantity, unitPrice）。linenumは持たない（明細はitemIdでキー付けする）。 */
    List<Line> lines = []

    /** 読み取り系の汎用レコード集合（R1=categoryId／R3=itemId+listPrice 等）。並び順は比較対象にしない（AC3）。 */
    List<Map<String, String>> entries = []

    /**
     * 比較前の正規化（AC1/AC3）: {@code inventoryDelta} をキー昇順に、{@code lines} をitemId昇順に、
     * {@code entries} を各エントリのcanonicalキー昇順に並べ替え、金額はscale(2)へ揃える。
     * 呼び出し元の状態は変更しない（新しいインスタンスを返す）。
     */
    ParitySnapshot normalize() {
        ParitySnapshot result = new ParitySnapshot()
        result.outcome = outcome
        result.inventoryDelta = inventoryDelta ? new TreeMap<String, Integer>(inventoryDelta) : [:]
        result.ordersCreated = ordersCreated
        result.orderTotal = normalizeAmount(orderTotal)
        result.lines = (lines ?: []).collect { Line line -> line.normalize() }.sort { it.itemId }
        result.entries = (entries ?: []).collect { normalizeEntry(it) }.sort { canonicalKey(it) }
        return result
    }

    private static Map<String, String> normalizeEntry(Map<String, String> entry) {
        return new TreeMap<String, String>(entry)
    }

    /** entry内の全キー=値を "|" 連結した文字列。entries全体の安定ソートキーとして使う。 */
    static String canonicalKey(Map<String, String> entry) {
        return entry.entrySet().collect { "${it.key}=${it.value}" }.join("|")
    }

    /** 金額文字列をscale(2)へ正規化する（HALF_UP）。nullはnullのまま。 */
    static String normalizeAmount(String amount) {
        if (amount == null) {
            return null
        }
        return new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP).toPlainString()
    }

    static class Line {
        String itemId
        Integer quantity
        String unitPrice

        Line normalize() {
            Line result = new Line()
            result.itemId = itemId
            result.quantity = quantity
            result.unitPrice = ParitySnapshot.normalizeAmount(unitPrice)
            return result
        }

        @Override
        boolean equals(Object other) {
            if (!(other instanceof Line)) {
                return false
            }
            Line that = (Line) other
            return itemId == that.itemId && quantity == that.quantity && unitPrice == that.unitPrice
        }

        @Override
        int hashCode() {
            return Objects.hash(itemId, quantity, unitPrice)
        }

        @Override
        String toString() {
            return "Line(itemId=${itemId}, quantity=${quantity}, unitPrice=${unitPrice})"
        }
    }
}
