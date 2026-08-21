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
            case "account-register":
                return accountRegister()
            case "account-edit-nopw":
                return accountEdit("nopw")
            case "account-edit-pw":
                return accountEdit("pw")
            case "account-edit-pwfield-absent":
                return accountEdit("pwfield-absent")
            case "orders-list":
                return ordersList()
            case "order-detail-own":
                return orderDetailOwn()
            case "order-detail-missing":
                return orderDetailMissing()
            case "cart-boundary":
                return cartBoundary()
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

    // ------------------------------------------------------------------
    // #51 T1: アカウント系（AC1/AC2・SM-1で3ケースに訂正）
    // ------------------------------------------------------------------

    private static final String W4_USERNAME = "parity_w4"
    private static final String W4_PASSWORD = "Parity-W4Pw!1"
    private static final Map<String, String> W5_USERNAMES = [
            "nopw"           : "parity_w5a",
            "pw"             : "parity_w5b",
            "pwfield-absent" : "parity_w5c",
    ]

    /**
     * W4: アカウント新規登録。canonicalは両側ともDB直読み（{@code m_account JOIN m_profile}）で組む
     * （design §4「canonicalは両側ともDB直読み・旧側と対称」・SM-4）。
     */
    private ParitySnapshot accountRegister() {
        long accountsBefore = db.accountCount()
        String json = MAPPER.writeValueAsString([
                username          : W4_USERNAME,
                password          : W4_PASSWORD,
                repeatedPassword  : W4_PASSWORD,
                email             : "parity.w4@example.com",
                firstName         : "Parity",
                lastName          : "W4",
                address1          : "1 Parity Way",
                address2          : "",
                city              : "Palo Alto",
                state             : "CA",
                postalCode        : "94303",
                country           : "USA",
                phone             : "555-0104",
                languagePreference: "english",
                favoriteCategoryId: "FISH",
        ])
        def response = http.postJson("/api/register", json)
        assertRegisterSucceeded(response, "W4(account-register)")
        long accountsAfter = db.accountCount()

        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.account = db.accountRow(W4_USERNAME)
        snapshot.accountsCreated = (int) (accountsAfter - accountsBefore)

        db.deleteAccountCascade(W4_USERNAME)

        return snapshot
    }

    /**
     * W5a/W5b/W5c: アカウント編集（自己完結方式・Q3）。SM-4の新側手順: {@code POST /api/register}で
     * 登録・自動ログイン → {@code GET /api/account}でversion取得 → {@code PUT /api/account} → （pwのみ）
     * {@code POST /api/account/password}（PUTを先・POST /passwordを後＝トークンローテーション対策）。
     */
    private ParitySnapshot accountEdit(String variant) {
        String username = W5_USERNAMES[variant]
        if (username == null) {
            throw new IllegalArgumentException("未対応のW5 variant: ${variant}")
        }
        String initialPassword = "Parity-${username}Pw!1"
        String newPassword = "Parity-${username}NewPw!2"

        String registerJson = MAPPER.writeValueAsString([
                username          : username,
                password          : initialPassword,
                repeatedPassword  : initialPassword,
                email             : "${username}@example.com".toString(),
                firstName         : "Parity",
                lastName          : "W5",
                address1          : "5 Parity Way",
                address2          : "",
                city              : "Palo Alto",
                state             : "CA",
                postalCode        : "94303",
                country           : "USA",
                phone             : "555-0105",
                languagePreference: "english",
                favoriteCategoryId: "FISH",
        ])
        def registerResponse = http.postJson("/api/register", registerJson)
        assertRegisterSucceeded(registerResponse, "W5(account-edit-${variant})")

        JsonNode current = MAPPER.readTree(http.get("/api/account").body())
        long version = current.get("version").asLong()

        String putJson = MAPPER.writeValueAsString([
                version               : version,
                firstName             : "Parity",
                lastName              : "W5-Edited",
                email                 : "${username}.edited@example.com".toString(),
                phone                 : "555-0106",
                address1              : "6 Parity Way",
                address2              : "",
                city                  : "Palo Alto",
                state                 : "CA",
                postalCode            : "94304",
                country               : "USA",
                languagePreference    : "english",
                favoriteCategoryId    : "DOGS",
                colorSchemePreference : "system",
        ])
        def putResponse = http.putJson("/api/account", putJson)
        if (putResponse.statusCode() != 200) {
            throw new IllegalStateException(
                    "W5(account-edit-${variant})のPUTに失敗: status=${putResponse.statusCode()} " +
                            "body=${putResponse.body()}")
        }

        // W5c(pwfield-absent)は新側に対応概念が無いためW5aと同一リクエストになる(=カバレッジ専用・
        // パリティ観測点ではない。account-edit-nopwと同じ判断)。
        if (variant == "pw") {
            String passwordJson = MAPPER.writeValueAsString(
                    [currentPassword: initialPassword, newPassword: newPassword])
            def passwordResponse = http.postJson("/api/account/password", passwordJson)
            if (passwordResponse.statusCode() != 204) {
                throw new IllegalStateException(
                        "W5(account-edit-pw)のPW変更に失敗: status=${passwordResponse.statusCode()} " +
                                "body=${passwordResponse.body()}")
            }
        }

        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.account = db.accountRow(username)

        db.deleteAccountCascade(username)

        return snapshot
    }

    // ------------------------------------------------------------------
    // #51 T2: 注文履歴照会（AC3）
    // ------------------------------------------------------------------

    /** R7: 注文一覧。旧の{@code ordersList()}と対称に、新規2件のtotalPriceのみをcanonicalへ格納する。 */
    private ParitySnapshot ordersList() {
        db.clearCart(userId)
        long maxOrderIdBefore = db.maxOrderId(userId) ?: 0L
        Map<String, Integer> qtyBefore = ["EST-1": db.inventoryQty("EST-1")]

        long orderId1 = placeOneOrder(1)
        long orderId2 = placeOneOrder(2)
        Set<String> expectedIds = [orderId1, orderId2]*.toString() as Set

        List<Map<String, String>> entries = []
        int page = 1
        int totalPages = 1
        while (page <= totalPages) {
            JsonNode body = MAPPER.readTree(http.get("/api/orders?page=${page}").body())
            body.get("content").each { JsonNode order ->
                if (order.get("orderId").asText() in expectedIds) {
                    String totalPrice = ParitySnapshot.normalizeAmount(order.get("totalPrice").decimalValue().toPlainString())
                    entries << ([totalPrice: totalPrice] as Map<String, String>)
                }
            }
            totalPages = body.get("totalPages").asInt()
            page++
        }
        if (entries.size() != 2) {
            throw new IllegalStateException(
                    "R7(orders-list)の前提が不成立: 新規作成したorderId=${expectedIds}が一覧から見つからない" +
                            "(entries=${entries})。")
        }

        db.deleteOrdersAbove(userId, maxOrderIdBefore)
        db.clearCart(userId)
        qtyBefore.each { String itemId, int qty -> db.setInventoryQty(itemId, qty) }

        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.entries = entries
        return snapshot
    }

    /** R8a: 注文詳細（自分の実在注文）。Q6・ID-24: {@code lines[].productName}はJOIN済みの実名を返す。 */
    private ParitySnapshot orderDetailOwn() {
        db.clearCart(userId)
        long maxOrderIdBefore = db.maxOrderId(userId) ?: 0L
        Map<String, Integer> qtyBefore = ["EST-1": db.inventoryQty("EST-1")]

        long orderId = placeOneOrder(2)

        def response = http.get("/api/orders/${orderId}")
        int httpStatus = response.statusCode()
        JsonNode body = MAPPER.readTree(response.body())

        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.httpStatus = httpStatus
        snapshot.orderTotal = body.get("totalPrice").decimalValue().toPlainString()
        snapshot.lines = body.get("lines").collect { JsonNode line ->
            new ParitySnapshot.Line(
                    itemId: line.get("itemId").asText(),
                    quantity: line.get("quantity").asInt(),
                    unitPrice: line.get("unitPrice").decimalValue().toPlainString(),
                    productName: line.get("productName").asText())
        }

        db.deleteOrdersAbove(userId, maxOrderIdBefore)
        db.clearCart(userId)
        qtyBefore.each { String itemId, int qty -> db.setInventoryQty(itemId, qty) }

        return snapshot
    }

    /**
     * R8b: 注文詳細（存在しないorderId）。新は{@code OrderApplicationService#getOrder}が不存在/非所有を
     * 同一の{@code AccessDeniedException}にし{@code GlobalExceptionHandler}が403へ正規化する（Q1・ID-14）。
     * {@code stackTraceExposed}は本文に例外クラス名/スタックフレームが含まれないことを実際に確認して求める
     * （決め打ちのfalseにしない）。
     *
     * <p>SM verification対応（Sprint21所見①と同型）: {@code GET /api/orders/{orderId}}は不在/非所有を
     * 同一の403にする（列挙封じ・訂正A）ため、前提（指定orderIdが実在しない）が採取時に崩れても
     * スナップショット（403・stackTraceExposed=false）だけでは区別できず、ID-14の観測点が静かに
     * 失われる。旧側（{@code LegacyScenarioRunner#orderDetailMissing}）と対称に、新側でも実行前に
     * DBへ問い合わせて前提を検証し、満たさなければ専用メッセージでfailさせる。
     */
    private ParitySnapshot orderDetailMissing() {
        long missingOrderId = 999999999L
        if (db.orderExists(missingOrderId)) {
            throw new IllegalStateException(
                    "R8b(order-detail-missing)の前提が不成立: orderId=${missingOrderId}が新側DBに実在する。" +
                            "ID-14の観測点(403がstale-session/不正ID起因であること)が成立しない。")
        }

        def response = http.get("/api/orders/${missingOrderId}")

        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.httpStatus = response.statusCode()
        snapshot.stackTraceExposed = containsLeakedException(response.body())
        return snapshot
    }

    /**
     * {@code POST /api/register}の成功(201)を検査する。{@code t_register_attempt}はPK=client_ip・5回/15分・
     * 成功時リセット無し（{@code V00_000_012}）のため、自己完結方式（W4+W5a/b/cで同一IPから4回登録）が
     * 残存行の後始末漏れで429に阻まれた場合、原因の切り分けが即座にできるよう専用メッセージでfailさせる
     * （Q3付随の必須対応）。
     */
    private static void assertRegisterSucceeded(def response, String context) {
        if (response.statusCode() == 429) {
            throw new IllegalStateException(
                    "${context}の登録がレート制限(429)に阻まれた: t_register_attemptのレート制限に当たった" +
                            "(5回/15分・client_ip単位・成功時リセット無し。残存行の後始末漏れを疑え)。" +
                            "body=${response.body()}")
        }
        if (response.statusCode() != 201) {
            throw new IllegalStateException(
                    "${context}の登録に失敗: status=${response.statusCode()} body=${response.body()}")
        }
    }

    private static boolean containsLeakedException(String body) {
        return body.contains("Exception") || body.contains("\tat ") || (body =~ /\bat [a-zA-Z0-9_.$]+\(/).find()
    }

    // ------------------------------------------------------------------
    // #51 T4: カート境界値（AC4・優先度は最後）
    // ------------------------------------------------------------------

    /**
     * cart-boundary: 旧の{@code cart-boundary}（{@link com.example.jpetstore.backend.parity.capture.LegacyScenarioRunner#cartBoundary}）と
     * 同じ観測可能な振る舞い（2回追加でqty=2／削除後に空／再削除でも200かつ空）を{@code CartController} REST APIで確認する。
     */
    private ParitySnapshot cartBoundary() {
        db.clearCart(userId)

        addToCart("EST-1", 1)
        addToCart("EST-1", 1)

        JsonNode afterAdd = MAPPER.readTree(http.get("/api/cart").body())
        List<JsonNode> itemsAfterAdd = afterAdd.get("items").toList()
        if (itemsAfterAdd.size() != 1 || itemsAfterAdd[0].get("itemId").asText() != "EST-1" ||
                itemsAfterAdd[0].get("quantity").asInt() != 2) {
            throw new IllegalStateException(
                    "cart-boundaryの前提が不成立(新側): 2回追加後のカート=${afterAdd}(期待値=EST-1 qty=2)。")
        }

        def firstRemove = http.delete("/api/cart/items/EST-1")
        JsonNode afterFirstRemove = MAPPER.readTree(firstRemove.body())
        boolean emptyAfterFirstRemove = afterFirstRemove.get("items").isEmpty()

        def secondRemove = http.delete("/api/cart/items/EST-1")
        int httpStatusAfterSecondRemove = secondRemove.statusCode()
        JsonNode afterSecondRemove = MAPPER.readTree(secondRemove.body())
        boolean emptyAfterSecondRemove = afterSecondRemove.get("items").isEmpty()

        if (!emptyAfterFirstRemove || !emptyAfterSecondRemove || httpStatusAfterSecondRemove != 200) {
            throw new IllegalStateException(
                    "cart-boundaryの前提が不成立(新側): emptyAfterFirstRemove=${emptyAfterFirstRemove}, " +
                            "emptyAfterSecondRemove=${emptyAfterSecondRemove}, " +
                            "httpStatusAfterSecondRemove=${httpStatusAfterSecondRemove}。")
        }

        db.clearCart(userId)

        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.entries = []
        return snapshot
    }

    /**
     * R7/R8a専用: 1件注文を確定し新しいorderIdを返す（後始末はしない・呼び出し元がまとめて担当）。
     * {@link #placeOrder}と異なり{@code beforeOrderSubmit}フック・後始末を持たない（複数回連続で呼ぶ用途のため）。
     */
    private long placeOneOrder(int quantity) {
        addToCart("EST-1", quantity)
        def response = http.postJson("/api/orders", placeOrderJson())
        if (response.statusCode() != 201) {
            throw new IllegalStateException(
                    "placeOneOrder(quantity=${quantity})が失敗: status=${response.statusCode()} body=${response.body()}")
        }
        JsonNode body = MAPPER.readTree(response.body())
        return body.get("orderId").asLong()
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
