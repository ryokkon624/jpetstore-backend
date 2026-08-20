package com.example.jpetstore.backend.parity.verify

import com.example.jpetstore.backend.parity.canonical.ParitySnapshot
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * シナリオID -&gt; 新側手順のレジストリ（#48 AC9・#49 AC1〜AC5・design.md §3）。
 *
 * <p>{@code capture.LegacyScenarioRunner} と対になる設計（自己完結で「前処理 → 駆動 → snapshot構築 →
 * 後始末」まで行う。AC10）。ログイン済みの{@link NewHttpClient}を受け取って使う。
 *
 * <p>注文系（W1/W2/W3）の後始末は**常に実施する**（legacyの{@code LegacyScenarioRunner}はW3のみ
 * コンテナ再作成に委ねて後始末を省略するが、新側はMySQL Testcontainersの単一コンテナを
 * テストスイート全体で共有するため、EST-1等の共有シード在庫を汚染しないよう毎回復元する＝
 * legacy/new非対称の意図的な設計判断）。
 */
class NewScenarioRunner {

    private static final List<String> CATEGORY_IDS = ["FISH", "DOGS", "CATS", "REPTILES", "BIRDS"]
    private static final List<String> SEARCH_BASIC_QUERIES = ["dog", "fish dog", "zzz-no-such-product-xyz"]
    private static final List<String> SEARCH_WILDCARD_QUERIES = ["%", "_"]
    private static final ObjectMapper MAPPER = new ObjectMapper()

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
                return placeOrder(["EST-1": 2])
            case "categories":
                return categories()
            case "products-by-category":
                return productsByCategory()
            case "items-by-product":
                return itemsByProduct()
            case "item-detail":
                return itemDetail()
            case "search-basic":
                return search(SEARCH_BASIC_QUERIES)
            case "search-wildcard":
                return search(SEARCH_WILDCARD_QUERIES)
            case "order-multi-item":
                return placeOrder(["EST-1": 2, "EST-4": 3])
            case "order-insufficient-stock":
                return orderInsufficientStock()
            default:
                throw new IllegalArgumentException("NewScenarioRunner未対応のシナリオ: ${scenarioId}")
        }
    }

    // ------------------------------------------------------------------
    // 読み取り系（#49 AC1/AC2/AC3）
    // ------------------------------------------------------------------

    private ParitySnapshot categories() {
        JsonNode body = MAPPER.readTree(http.get("/api/categories").body())
        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.entries = body.collect { JsonNode c -> [categoryId: c.get("categoryId").asText()] as Map<String, String> }
        return snapshot
    }

    private ParitySnapshot productsByCategory() {
        List<Map<String, String>> entries = []
        CATEGORY_IDS.each { String categoryId ->
            walkAllPages("/api/categories/${categoryId}/products") { JsonNode item ->
                entries << [categoryId: categoryId, productId: item.get("productId").asText()]
            }
        }
        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.entries = entries
        return snapshot
    }

    private ParitySnapshot itemsByProduct() {
        String productId = "FI-SW-01"
        List<Map<String, String>> entries = []
        walkAllPages("/api/products/${productId}/items") { JsonNode item ->
            entries << [itemId: item.get("itemId").asText(), listPrice: item.get("listPrice").decimalValue().toPlainString()]
        }
        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.entries = entries
        return snapshot
    }

    private ParitySnapshot itemDetail() {
        JsonNode body = MAPPER.readTree(http.get("/api/items/EST-1").body())
        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.entries = [[
                itemId     : body.get("itemId").asText(),
                productName: body.get("productName").asText(),
                listPrice  : body.get("listPrice").decimalValue().toPlainString(),
        ]]
        return snapshot
    }

    private ParitySnapshot search(List<String> queries) {
        List<Map<String, String>> entries = []
        queries.each { String query ->
            String encoded = URLEncoder.encode(query, "UTF-8")
            walkAllPages("/api/products/search?keyword=${encoded}") { JsonNode item ->
                entries << [query: query, productId: item.get("productId").asText()]
            }
        }
        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.entries = entries
        return snapshot
    }

    /** {@code PageResponse}を{@code page=1..totalPages}まで辿り、各要素へ{@code perItem}を適用する。 */
    private void walkAllPages(String basePath, Closure perItem) {
        int page = 1
        int totalPages = 1
        String separator = basePath.contains("?") ? "&" : "?"
        while (page <= totalPages) {
            JsonNode body = MAPPER.readTree(http.get("${basePath}${separator}page=${page}").body())
            body.get("content").each { JsonNode item -> perItem(item) }
            totalPages = body.get("totalPages").asInt()
            page++
        }
    }

    // ------------------------------------------------------------------
    // 状態変更系（#49 AC4/AC5）
    // ------------------------------------------------------------------

    /**
     * W3: 在庫不足。新側は{@code POST /api/cart/items}自体が在庫超過を400で拒否するため
     * （legacyには無いカートレベルのガード）、legacyと同じ「在庫はあるうちにカートへ追加し、
     * その後在庫が減った」状況を再現する必要がある（{@code OrderControllerSpec}の
     * 「在庫不足(競合負けを含む)」テストと同型）。{@link #placeOrder}の{@code beforeOrderSubmit}
     * フックでカート追加後・注文確定直前に在庫を1へ強制し、注文確定APIの在庫ガード（409）を踏ませる。
     */
    private ParitySnapshot orderInsufficientStock() {
        String itemId = "EST-1"
        int trueOriginalQty = db.inventoryQty(itemId)
        ParitySnapshot snapshot = placeOrder([(itemId): 2]) { db.setInventoryQty(itemId, 1) }
        // placeOrder内部の復元は「beforeOrderSubmitで下げた1」までしか戻さないため、真の初期値へ上書きし直す。
        db.setInventoryQty(itemId, trueOriginalQty)
        return snapshot
    }

    /**
     * W1/W2/W3共通の注文確定手順。legacyと同じitemId・数量で駆動する（design.md §7.1）。
     *
     * @param beforeOrderSubmit カート追加完了後・{@code POST /api/orders}直前に実行する任意のフック
     *     （W3が在庫を強制的に減らすために使う。同時発注による在庫減少を再現）。
     */
    private ParitySnapshot placeOrder(Map<String, Integer> items, Closure beforeOrderSubmit = null) {
        db.clearCart(userId) // AC10: シナリオ前処理(カート残留対策・F4の新側担当分)
        long orderCountBefore = db.orderCount(userId)
        long maxOrderIdBefore = db.maxOrderId(userId) ?: 0L

        items.each { String itemId, int qty -> addToCart(itemId, qty) }
        beforeOrderSubmit?.call()
        // 在庫デルタの基準点は「注文確定APIを呼ぶ直前」に統一する(legacyのorderInsufficientStockは
        // 前処理〔在庫を1へ〕がplaceOrder呼び出し"前"に完了しているためqtyBefore=1から始まるのに対し、
        // 新側はカート追加"後"にbeforeOrderSubmitで在庫を下げるため、ここで捕捉しないと
        // 「カート追加前の本来の在庫」との差分になり無意味な大きな値になってしまう)。
        Map<String, Integer> qtyBefore = items.collectEntries { itemId, qty -> [(itemId): db.inventoryQty(itemId)] }

        def response = http.postJson("/api/orders", placeOrderJson())
        boolean succeeded = response.statusCode() == 201

        long orderCountAfter = db.orderCount(userId)
        long maxOrderIdAfter = db.maxOrderId(userId) ?: 0L

        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.outcome = succeeded ? "SUCCESS" : "FAILURE"
        snapshot.inventoryDelta = items.keySet().collectEntries { String itemId ->
            [(itemId): db.inventoryQty(itemId) - qtyBefore[itemId]]
        }
        snapshot.ordersCreated = (int) (orderCountAfter - orderCountBefore)
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

        // 後始末: 常に実施する(新側はMySQL共有コンテナのためlegacyのW3特例と非対称。クラスjavadoc参照)。
        if (succeeded) {
            db.deleteOrdersAbove(userId, maxOrderIdBefore)
        }
        db.clearCart(userId)
        qtyBefore.each { String itemId, int qty -> db.setInventoryQty(itemId, qty) }

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
