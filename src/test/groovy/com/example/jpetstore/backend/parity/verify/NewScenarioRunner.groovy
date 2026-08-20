package com.example.jpetstore.backend.parity.verify

import com.example.jpetstore.backend.parity.canonical.ParitySnapshot

/**
 * シナリオID -&gt; 新側手順のレジストリ（#48 AC9・design.md §3）。★#49はここにメソッドを足すだけで横展開する。
 *
 * <p>{@code capture.LegacyScenarioRunner} と対になる設計（自己完結で「前処理 → 駆動 → snapshot構築 →
 * 後始末」まで行う。AC10）。ログイン済みの{@link NewHttpClient}を受け取って使う。
 */
class NewScenarioRunner {

    private final NewHttpClient http
    private final NewDbReader db
    private final long userId

    NewScenarioRunner(NewHttpClient http, NewDbReader db, long userId) {
        this.http = http
        this.db = db
        this.userId = userId
    }

    ParitySnapshot run(String scenarioId) {
        switch (scenarioId) {
            case "order-single-item":
                return orderSingleItem()
            default:
                throw new IllegalArgumentException("NewScenarioRunner未対応のシナリオ: ${scenarioId}")
        }
    }

    /** W1: 単一商品（EST-1 x2）の注文確定。legacy側と同じ商品・数量で駆動する（design.md §7.1）。 */
    private ParitySnapshot orderSingleItem() {
        String itemId = "EST-1"
        db.clearCart(userId) // AC10: シナリオ前処理(カート残留対策・F4の新側担当分)
        int qtyBefore = db.inventoryQty(itemId)
        long maxOrderIdBefore = db.maxOrderId(userId) ?: 0L

        addToCart(itemId, 2)
        def response = http.postJson("/api/orders", placeOrderJson())
        boolean succeeded = response.statusCode() == 201

        int qtyAfter = db.inventoryQty(itemId)
        long maxOrderIdAfter = db.maxOrderId(userId) ?: 0L

        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.outcome = succeeded ? "SUCCESS" : "FAILURE"
        snapshot.inventoryDelta = [(itemId): qtyAfter - qtyBefore]
        snapshot.ordersCreated = (int) (maxOrderIdAfter - maxOrderIdBefore)
        if (succeeded) {
            Map<String, Object> order = db.orderRow(maxOrderIdAfter)
            snapshot.orderTotal = order.total_price.toString()
            snapshot.lines = db.orderLines(maxOrderIdAfter).collect { row ->
                new ParitySnapshot.Line(
                        itemId: row.item_id as String,
                        quantity: row.quantity as Integer,
                        unitPrice: row.unit_price.toString())
            }
        }

        // 後始末: 増えた注文・カートを削除し在庫を復元する(次のシナリオ・再実行のため)。
        if (succeeded) {
            db.deleteOrdersAbove(userId, maxOrderIdBefore)
        }
        db.clearCart(userId)
        db.setInventoryQty(itemId, qtyBefore)

        return snapshot
    }

    private void addToCart(String itemId, int quantity) {
        String json = /{"itemId":"${itemId}","quantity":${quantity}}/
        def response = http.postJson("/api/cart/items", json)
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "addToCart失敗: status=${response.statusCode()} body=${response.body()}")
        }
    }

    /** legacyのNewOrderForm.jsp相当のダミー入力(design.md §7.1実測と揃える)。 */
    private static String placeOrderJson() {
        return '''
            {"billing": {"firstName":"ABC","lastName":"XYX","address1":"901 San Antonio Road",
                         "address2":"MS UCUP02-206","city":"Palo Alto","state":"CA",
                         "postalCode":"94303","country":"USA"},
             "useSeparateShipping": false}
        '''
    }
}
