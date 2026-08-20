package com.example.jpetstore.backend.parity.capture

import com.example.jpetstore.backend.parity.canonical.ParitySnapshot

/**
 * シナリオID -&gt; 旧側手順のレジストリ（#48 AC6・design.md §3）。★#49はここにメソッドを足すだけで横展開する。
 *
 * <p>各メソッドは自己完結で「実行前ベースライン記録 → 駆動 → snapshot構築 → 後始末（復元）」まで行う
 * （AC10・AC-neg2・D2）。W3（在庫不足）のみコンテナ再作成が前提のため後始末をここで完結させない
 * （#49で追加）。
 */
class LegacyScenarioRunner {

    private final LegacyHttpClient http
    private final LegacyDbReader db

    LegacyScenarioRunner(LegacyHttpClient http, LegacyDbReader db) {
        this.http = http
        this.db = db
    }

    ParitySnapshot run(String scenarioId) {
        switch (scenarioId) {
            case "order-single-item":
                return orderSingleItem()
            default:
                throw new IllegalArgumentException("LegacyScenarioRunner未対応のシナリオ: ${scenarioId}")
        }
    }

    /**
     * W1: 単一商品（EST-1 x2）の注文確定（design.md §7.1で実証済みの駆動経路）。
     * 駆動: signon.do -&gt; addItemToCart.do -&gt; updateCartQuantities.do -&gt; checkout.do -&gt;
     * newOrderForm.do -&gt; newOrder.do(order.*フォーム) -&gt; newOrder.do?confirmed=true
     */
    private ParitySnapshot orderSingleItem() {
        http.resetSession() // F4: シナリオごとに新しいHTTPセッションを張る
        String itemId = "EST-1"
        int qtyBefore = db.inventoryQty(itemId)
        long maxOrderIdBefore = db.maxOrderId()
        long linenumBefore = db.sequenceNextId("linenum")
        long ordernumBefore = db.sequenceNextId("ordernum")

        http.postForm("/signon.do", [username: "j2ee", password: "j2ee"])
        http.get("/addItemToCart.do?workingItemId=${itemId}")
        http.postForm("/updateCartQuantities.do", [(itemId): "2"])
        http.get("/checkout.do")
        http.get("/newOrderForm.do")
        http.postForm("/newOrder.do", newOrderFormFields())
        http.get("/newOrder.do?confirmed=true")

        int qtyAfter = db.inventoryQty(itemId)
        long maxOrderIdAfter = db.maxOrderId()

        ParitySnapshot snapshot = new ParitySnapshot()
        boolean succeeded = maxOrderIdAfter > maxOrderIdBefore
        snapshot.outcome = succeeded ? "SUCCESS" : "FAILURE"
        snapshot.inventoryDelta = [(itemId): qtyAfter - qtyBefore]
        snapshot.ordersCreated = (int) (maxOrderIdAfter - maxOrderIdBefore)
        if (succeeded) {
            Map<String, Object> order = db.orderRow(maxOrderIdAfter)
            snapshot.orderTotal = order.totalprice.toString()
            snapshot.lines = db.orderLines(maxOrderIdAfter).collect { row ->
                new ParitySnapshot.Line(
                        itemId: row.itemid as String,
                        quantity: (row.quantity as Number).intValue(),
                        unitPrice: row.unitprice.toString())
            }
        }

        // 後始末(AC-neg2・D2): 増加分のlineitem/orderstatus/ordersを削除、inventory・sequenceを復元。
        if (succeeded) {
            db.deleteOrdersAbove(maxOrderIdBefore)
            db.restoreSequenceNextId("ordernum", ordernumBefore)
            db.restoreSequenceNextId("linenum", linenumBefore)
        }
        db.restoreInventoryQty(itemId, qtyBefore)

        return snapshot
    }

    /** NewOrderForm.jsp相当のダミー入力(design.md §7.1実測)。card/address系はcanonical比較対象外(ID-8等)。 */
    private static Map<String, String> newOrderFormFields() {
        return [
                "order.cardType"       : "Visa",
                "order.creditCard"     : "999 9999 9999 9999",
                "order.expiryDate"     : "12/03",
                "order.billToFirstName": "ABC",
                "order.billToLastName" : "XYX",
                "order.billAddress1"   : "901 San Antonio Road",
                "order.billAddress2"   : "MS UCUP02-206",
                "order.billCity"       : "Palo Alto",
                "order.billState"      : "CA",
                "order.billZip"        : "94303",
                "order.billCountry"    : "USA",
        ]
    }
}
