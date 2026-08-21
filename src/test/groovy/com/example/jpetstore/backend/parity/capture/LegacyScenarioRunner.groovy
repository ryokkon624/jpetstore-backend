package com.example.jpetstore.backend.parity.capture

import com.example.jpetstore.backend.parity.canonical.ParitySnapshot

import java.util.regex.Matcher

/**
 * シナリオID -&gt; 旧側手順のレジストリ（#48 AC6・#49 AC1〜AC5・design.md §3）。
 *
 * <p>各メソッドは自己完結で「実行前ベースライン記録 → 駆動 → snapshot構築 → 後始末（復元）」まで行う
 * （AC10・AC-neg2・D2）。読み取り系（R1〜R6）はリセット不要（実行前処理は{@code resetSession()}のみ）。
 * {@code order-insufficient-stock}（W3）のみ、意図的に後始末を行わない（採取順の最後に置き、
 * 採取終了後にコンテナを作り直して初期シードへ戻す＝D2）。
 */
class LegacyScenarioRunner {

    private static final List<String> CATEGORY_IDS = ["FISH", "DOGS", "CATS", "REPTILES", "BIRDS"]
    private static final List<String> SEARCH_BASIC_QUERIES = ["dog", "fish dog", "zzz-no-such-product-xyz"]
    private static final List<String> SEARCH_WILDCARD_QUERIES = ["%", "_"]

    private final LegacyHttpClient http
    private final LegacyDbReader db

    LegacyScenarioRunner(LegacyHttpClient http, LegacyDbReader db) {
        this.http = http
        this.db = db
    }

    /**
     * captureの戻り値。{@code preconditions}はcanonical比較の対象外のメタ情報
     * （SM verification対応(c)・golden {@code snapshot}には入れず{@code capturedFrom}と同階層に置く）。
     * ほとんどのシナリオでは{@code null}のまま（W3のみ使用）。
     */
    static class CaptureResult {
        final ParitySnapshot snapshot
        final Map<String, Map<String, Object>> preconditions

        CaptureResult(ParitySnapshot snapshot, Map<String, Map<String, Object>> preconditions = null) {
            this.snapshot = snapshot
            this.preconditions = preconditions
        }
    }

    CaptureResult run(String scenarioId) {
        switch (scenarioId) {
            case "order-single-item":
                return new CaptureResult(placeOrder(["EST-1": 2], true))
            case "categories":
                return new CaptureResult(categories())
            case "products-by-category":
                return new CaptureResult(productsByCategory())
            case "items-by-product":
                return new CaptureResult(itemsByProduct())
            case "item-detail":
                return new CaptureResult(itemDetail())
            case "search-basic":
                return new CaptureResult(search(SEARCH_BASIC_QUERIES))
            case "search-wildcard":
                return new CaptureResult(search(SEARCH_WILDCARD_QUERIES))
            case "order-multi-item":
                return new CaptureResult(placeOrder(["EST-1": 2, "EST-4": 3], true))
            case "account-register":
                return new CaptureResult(accountRegister())
            case "account-edit-nopw":
                return new CaptureResult(accountEdit("nopw"))
            case "account-edit-pw":
                return new CaptureResult(accountEdit("pw"))
            case "account-edit-pwfield-absent":
                return new CaptureResult(accountEdit("pwfield-absent"))
            case "orders-list":
                return new CaptureResult(ordersList())
            case "order-detail-own":
                return orderDetailOwn()
            case "order-detail-missing":
                return orderDetailMissing()
            case "cart-boundary":
                return new CaptureResult(cartBoundary())
            case "order-insufficient-stock":
                return orderInsufficientStock()
            default:
                throw new IllegalArgumentException("LegacyScenarioRunner未対応のシナリオ: ${scenarioId}")
        }
    }

    // ------------------------------------------------------------------
    // 読み取り系（#49 AC1/AC2/AC3）
    // ------------------------------------------------------------------

    /** R1: カテゴリ一覧。動的な{@code getCategoryList()}の呼び出し元は{@code newAccountForm.do}のセレクトのみ。 */
    private ParitySnapshot categories() {
        http.resetSession()
        String html = http.get("/newAccountForm.do").body()
        List<String> categoryIds = LegacyHtmlExtractor.extractSelectOptions(html, "account.favouriteCategoryId")
        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.entries = categoryIds.collect { [categoryId: it] as Map<String, String> }
        return snapshot
    }

    /** R2: カテゴリ配下の商品一覧(5カテゴリ全件・全ページ走査＝F5)。 */
    private ParitySnapshot productsByCategory() {
        http.resetSession()
        List<Map<String, String>> entries = []
        CATEGORY_IDS.each { String categoryId ->
            List<String> productIds = walkPages("/viewCategory.do?categoryId=${categoryId}") { String html ->
                LegacyHtmlExtractor.extractProductIds(html)
            }
            productIds.each { String productId -> entries << [categoryId: categoryId, productId: productId] }
        }
        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.entries = entries
        return snapshot
    }

    /** R3: 商品配下のアイテム一覧(FI-SW-01・itemId＋listPrice・全ページ走査)。 */
    private ParitySnapshot itemsByProduct() {
        http.resetSession()
        String productId = "FI-SW-01"
        List<Map<String, String>> rows = walkPages("/viewProduct.do?productId=${productId}") { String html ->
            LegacyHtmlExtractor.extractItemRows(html)
        }
        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.entries = rows
        return snapshot
    }

    /**
     * R4: アイテム詳細(EST-1)。productNameはItem.jsp上でattribute群と連結されHTML抽出が非現実的なため
     * DBから読む(design.md §6-2)。itemId/listPriceはHTTP応答から抽出する。
     */
    private ParitySnapshot itemDetail() {
        http.resetSession()
        String itemId = "EST-1"
        String html = http.get("/viewItem.do?itemId=${itemId}").body()
        Matcher idMatcher = (html =~ /<td width="100%" bgcolor="#cccccc">\s*<b>([A-Za-z0-9-]+)<\/b>/)
        String extractedItemId = idMatcher.find() ? idMatcher.group(1) : null
        // 実機ではfmt:formatNumberがフォーマットを適用せず生の数値が出る(LegacyHtmlExtractor参照)。
        String listPrice = LegacyHtmlExtractor.extractPlainDecimal(html)
        String productName = db.productNameForItem(itemId)

        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.entries = [[itemId: extractedItemId, productName: productName, listPrice: listPrice]]
        return snapshot
    }

    /** R5/R6共通: 複数キーワードをsearchProducts.doへ順に投げ、全ページ走査したproductIdをqueryタグ付きで集約する。 */
    private ParitySnapshot search(List<String> queries) {
        http.resetSession()
        List<Map<String, String>> entries = []
        queries.each { String query ->
            List<String> productIds = walkPages("/searchProducts.do", [search: "true", keyword: query]) { html ->
                LegacyHtmlExtractor.extractProductIds(html)
            }
            productIds.each { String productId -> entries << [query: query, productId: productId] }
        }
        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.entries = entries
        return snapshot
    }

    // ------------------------------------------------------------------
    // 状態変更系（#49 AC4/AC5）
    // ------------------------------------------------------------------

    /**
     * W3: 在庫不足(在庫を注文数未満へ強制した状態でEST-1x2を注文)。旧は在庫ガード無し(ID-1)のため成功し
     * 在庫がマイナスになる。**採取順の最後に置き、後始末(復元)は行わない**(D2・コンテナ再作成に委ねる)。
     *
     * <p>SM verification対応(a)(b): 前処理UPDATEが黙って0行になっても（itemId不一致・表定義変更等）
     * legacyは在庫が変わらず注文が成功するだけで、goldenは「在庫が十分な通常成功ケース」とバイト同一に
     * なりうる（ID-1の観測点が静かに失われる）。**前提（在庫&lt;注文数）と結果（在庫&lt;0）を実際にDBへ
     * 問い合わせて検証し、満たさなければgoldenを書き出さずにfailさせる**。検証した実測値は
     * {@code preconditions}としてgoldenのメタ情報へ残す（(c)・snapshotには入れずcanonical比較対象外）。
     */
    private CaptureResult orderInsufficientStock() {
        String itemId = "EST-1"
        int forcedQty = 1
        int orderQty = 2
        db.restoreInventoryQty(itemId, forcedQty) // 前処理: 在庫を注文数未満へ強制セット(affected rows検査込み)

        int qtyBeforeOrder = db.inventoryQty(itemId)
        if (qtyBeforeOrder >= orderQty) {
            throw new IllegalStateException(
                    "W3(order-insufficient-stock)の前提が不成立: itemId=${itemId}の在庫=${qtyBeforeOrder}が" +
                            "注文数${orderQty}以上(在庫不足の状態を再現できていない)。goldenは書き出さない。")
        }

        ParitySnapshot snapshot = placeOrder([(itemId): orderQty], false)

        int qtyAfterOrder = db.inventoryQty(itemId)
        if (qtyAfterOrder >= 0) {
            throw new IllegalStateException(
                    "W3(order-insufficient-stock)のID-1実証が不成立: itemId=${itemId}の注文後在庫=" +
                            "${qtyAfterOrder}が0以上(旧の無条件減算でマイナス化するはずが実測されなかった)。" +
                            "goldenは書き出さない。")
        }

        return new CaptureResult(snapshot, [(itemId): [qtyBefore: qtyBeforeOrder, qtyAfter: qtyAfterOrder]])
    }

    /**
     * W1/W2共通の注文確定手順(design.md §7.1で実証済みの駆動経路)。
     * 駆動: signon.do -&gt; addItemToCart.do(itemごと) -&gt; updateCartQuantities.do(一括) -&gt; checkout.do ->
     * newOrderForm.do -&gt; newOrder.do(order.*フォーム) -&gt; newOrder.do?confirmed=true
     *
     * @param items itemId -&gt; 注文数量
     * @param restoreAfter 後始末(増加分の削除・sequence/inventory復元)を行うか。W3のみfalse(D2)。
     */
    private ParitySnapshot placeOrder(Map<String, Integer> items, boolean restoreAfter) {
        http.resetSession() // F4: シナリオごとに新しいHTTPセッションを張る
        Map<String, Integer> qtyBefore = items.collectEntries { itemId, qty -> [(itemId): db.inventoryQty(itemId)] }
        long orderCountBefore = db.orderCount()
        long maxOrderIdBefore = db.maxOrderId()
        long linenumBefore = db.sequenceNextId("linenum")
        long ordernumBefore = db.sequenceNextId("ordernum")

        http.postForm("/signon.do", [username: "j2ee", password: "j2ee"])
        items.keySet().each { String itemId -> http.get("/addItemToCart.do?workingItemId=${itemId}") }
        Map<String, String> cartForm = items.collectEntries { itemId, qty -> [(itemId): qty.toString()] }
        http.postForm("/updateCartQuantities.do", cartForm)
        http.get("/checkout.do")
        http.get("/newOrderForm.do")
        http.postForm("/newOrder.do", newOrderFormFields())
        http.get("/newOrder.do?confirmed=true")

        long orderCountAfter = db.orderCount()
        long maxOrderIdAfter = db.maxOrderId()
        boolean succeeded = orderCountAfter > orderCountBefore

        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.outcome = succeeded ? "SUCCESS" : "FAILURE"
        snapshot.inventoryDelta = items.keySet().collectEntries { String itemId ->
            [(itemId): db.inventoryQty(itemId) - qtyBefore[itemId]]
        }
        snapshot.ordersCreated = (int) (orderCountAfter - orderCountBefore)
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

        if (restoreAfter) {
            if (succeeded) {
                db.deleteOrdersAbove(maxOrderIdBefore)
                db.restoreSequenceNextId("ordernum", ordernumBefore)
                db.restoreSequenceNextId("linenum", linenumBefore)
            }
            qtyBefore.each { String itemId, int qty -> db.restoreInventoryQty(itemId, qty) }
        }

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
     * W4: アカウント新規登録。{@code account.listOption}/{@code account.bannerOption}パラメータは送らない
     * （＝{@code Account}のfalse側2アウトカム。SM-5: パラメータ非送信=false）。
     * 前提: {@code BANNERDATA}にFISHが存在すること（{@code getAccountByUsername}のINNER JOIN依存・AC1）。
     */
    private ParitySnapshot accountRegister() {
        http.resetSession()
        if (!db.bannerDataHasCategory("FISH")) {
            throw new IllegalStateException(
                    "W4(account-register)の前提が不成立: BANNERDATAにFISHが存在しない" +
                            "(getAccountByUsernameはbannerdataとINNER JOINするため必須)。goldenは書き出さない。")
        }
        long accountsBefore = db.accountCount()

        http.get("/newAccountForm.do")
        http.postForm("/newAccount.do", [
                validate                    : "newAccount",
                "account.username"          : W4_USERNAME,
                "account.password"          : W4_PASSWORD,
                repeatedPassword            : W4_PASSWORD,
                "account.firstName"         : "Parity",
                "account.lastName"          : "W4",
                "account.email"             : "parity.w4@example.com",
                "account.phone"             : "555-0104",
                "account.address1"          : "1 Parity Way",
                "account.address2"          : "",
                "account.city"              : "Palo Alto",
                "account.state"             : "CA",
                "account.zip"               : "94303",
                "account.country"           : "USA",
                "account.languagePreference": "english",
                "account.favouriteCategoryId": "FISH",
                // account.listOption/account.bannerOptionは送らない(=Accountのfalse側2アウトカム)
        ])

        long accountsAfter = db.accountCount()
        if (accountsAfter != accountsBefore + 1) {
            throw new IllegalStateException(
                    "W4(account-register)の登録が成立しなかった: accountsBefore=${accountsBefore}, " +
                            "accountsAfter=${accountsAfter}(期待値=accountsBefore+1)。goldenは書き出さない。")
        }

        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.account = db.accountRow(W4_USERNAME)
        snapshot.accountsCreated = (int) (accountsAfter - accountsBefore)

        db.deleteAccountCascade(W4_USERNAME)

        return snapshot
    }

    /**
     * W5a/W5b/W5c: アカウント編集（自己完結方式・Q3）。編集対象は本メソッド自身が{@link #accountRegister}と同じ
     * 登録経路で作る（{@code j2ee}は不変のまま・AC2）。
     *
     * <p>{@code account.listOption}/{@code account.bannerOption}は毎回送る（＝{@code Account}の残り2アウトカムの
     * true側）。パスワード欄のみをvariantで変える（SM-1の3ケース）:
     * <ul>
     *   <li>{@code nopw}（W5a）: {@code account.password=""}＋{@code repeatedPassword=""} → 分岐1-true/分岐2-false</li>
     *   <li>{@code pw}（W5b）: 新PWを送る → 分岐2-true（updateSignonが走る）</li>
     *   <li>{@code pwfield-absent}（W5c）: {@code account.password}パラメータ自体を送らない → 分岐1-false。
     *       {@code workingAccountForm}はsession scopeのため、登録直後の{@code editAccountForm.do}呼び出し直後
     *       （＝DBから読み直した直後でaccount.password=null）に実行する必要がある（R7参照・SM-1）。</li>
     * </ul>
     */
    private ParitySnapshot accountEdit(String variant) {
        String username = W5_USERNAMES[variant]
        if (username == null) {
            throw new IllegalArgumentException("未対応のW5 variant: ${variant}")
        }
        String initialPassword = "Parity-${username}Pw!1"
        String newPassword = "Parity-${username}NewPw!2"

        http.resetSession()
        if (!db.bannerDataHasCategory("FISH")) {
            throw new IllegalStateException(
                    "W5(account-edit-${variant})の前提が不成立: BANNERDATAにFISHが存在しない。goldenは書き出さない。")
        }

        // 編集対象を登録経路で作る(自己完結方式・Q3)。NewAccountActionがsession.accountFormを直接設定するため
        // 登録成功時点で自動的にsignon済みになる(SecureBaseAction配下のeditAccountForm.do/editAccount.doが
        // 追加のsignon.do無しで通る)。
        // ★newAccountForm.doを先にGETしてsession.workingAccountForm.accountを空Accountで初期化しておく
        // (NewAccountFormActionが行う)。省略するとBeanUtils.populateが"No bean specified"で500になる
        // (実機で確認済み)。
        http.get("/newAccountForm.do")
        http.postForm("/newAccount.do", [
                validate                    : "newAccount",
                "account.username"          : username,
                "account.password"          : initialPassword,
                repeatedPassword            : initialPassword,
                "account.firstName"         : "Parity",
                "account.lastName"          : "W5",
                "account.email"             : "${username}@example.com".toString(),
                "account.phone"             : "555-0105",
                "account.address1"          : "5 Parity Way",
                "account.address2"          : "",
                "account.city"              : "Palo Alto",
                "account.state"             : "CA",
                "account.zip"               : "94303",
                "account.country"           : "USA",
                "account.languagePreference": "english",
                "account.favouriteCategoryId": "FISH",
        ])
        if (db.signonPassword(username) == null) {
            throw new IllegalStateException(
                    "W5(account-edit-${variant})の編集対象登録が成立しなかった: username=${username}の" +
                            "SIGNON行が見つからない。goldenは書き出さない。")
        }
        // workingAccountFormをDBから再ロードさせる(account.password=null側からスタート。R7・SM-1)。
        http.get("/editAccountForm.do")

        Map<String, String> editForm = [
                validate                    : "editAccount",
                "account.username"          : username,
                "account.firstName"         : "Parity",
                "account.lastName"          : "W5-Edited",
                "account.email"             : "${username}.edited@example.com".toString(),
                "account.phone"             : "555-0106",
                "account.address1"          : "6 Parity Way",
                "account.address2"          : "",
                "account.city"              : "Palo Alto",
                "account.state"             : "CA",
                "account.zip"               : "94304",
                "account.country"           : "USA",
                "account.languagePreference": "english",
                "account.favouriteCategoryId": "DOGS",
                "account.listOption"        : "on",
                "account.bannerOption"      : "on",
        ]

        String passwordBefore = db.signonPassword(username)
        boolean expectedChanged
        switch (variant) {
            case "nopw":
                editForm["account.password"] = ""
                editForm["repeatedPassword"] = ""
                expectedChanged = false
                break
            case "pw":
                editForm["account.password"] = newPassword
                editForm["repeatedPassword"] = newPassword
                expectedChanged = true
                break
            case "pwfield-absent":
                // account.password/repeatedPasswordパラメータ自体を送らない
                expectedChanged = false
                break
        }
        http.postForm("/editAccount.do", editForm)

        String passwordAfter = db.signonPassword(username)
        boolean actuallyChanged = passwordBefore != passwordAfter
        if (actuallyChanged != expectedChanged) {
            throw new IllegalStateException(
                    "W5(account-edit-${variant})のsignon.password前提が不成立: before=${passwordBefore}, " +
                            "after=${passwordAfter}, expectedChanged=${expectedChanged}。goldenは書き出さない。")
        }

        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.account = db.accountRow(username)

        db.deleteAccountCascade(username)

        return snapshot
    }

    // ------------------------------------------------------------------
    // #51 T2: 注文履歴照会（AC3）
    // ------------------------------------------------------------------

    /**
     * R7: 注文一覧。{@code j2ee}は既存注文履歴を持つため、baseline（登録前）とafter（2件登録後）の差分
     * （orderId集合）で「一覧に自分の新規注文が正しく現れるか」を検証する。canonicalの{@code entries}には
     * 新規2件のtotalPriceのみを格納する（orderId自体は採番機構が違うため比較対象に含めない・ID-23と同型）。
     */
    private ParitySnapshot ordersList() {
        http.resetSession()
        http.postForm("/signon.do", [username: "j2ee", password: "j2ee"])

        long maxOrderIdBefore = db.maxOrderId()
        long linenumBefore = db.sequenceNextId("linenum")
        long ordernumBefore = db.sequenceNextId("ordernum")
        Map<String, Integer> qtyBefore = ["EST-1": db.inventoryQty("EST-1")]

        long orderId1 = placeOneOrder(["EST-1": 1])
        long orderId2 = placeOneOrder(["EST-1": 2])
        Set<String> expectedIds = [orderId1, orderId2]*.toString() as Set

        String html = http.get("/listOrders.do").body()
        List<Map<String, String>> allRows = LegacyHtmlExtractor.extractOrderListRows(html)
        Set<String> presentIds = allRows*.orderId as Set
        if (!presentIds.containsAll(expectedIds)) {
            throw new IllegalStateException(
                    "R7(orders-list)の前提が不成立: 新規作成したorderId=${expectedIds}が一覧(${presentIds})に" +
                            "現れていない。goldenは書き出さない。")
        }

        List<Map<String, String>> entries = allRows.findAll { it.orderId in expectedIds }
                .collect { [totalPrice: ParitySnapshot.normalizeAmount(it.totalPrice)] as Map<String, String> }

        db.deleteOrdersAbove(maxOrderIdBefore)
        db.restoreSequenceNextId("ordernum", ordernumBefore)
        db.restoreSequenceNextId("linenum", linenumBefore)
        qtyBefore.each { String itemId, int qty -> db.restoreInventoryQty(itemId, qty) }

        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.entries = entries
        return snapshot
    }

    /**
     * R8a: 注文詳細（自分の実在注文）。{@code lines[EST-1].productName}はQ6でINTENDED_DIVERGENCE(ID-24)の
     * 観測点にした。前提「旧の注文詳細で商品名が空」を採取時にassertし、満たさなければgoldenを書き出さず
     * failさせる（SM-3をR8aにも適用）。
     */
    private CaptureResult orderDetailOwn() {
        http.resetSession()
        http.postForm("/signon.do", [username: "j2ee", password: "j2ee"])

        long maxOrderIdBefore = db.maxOrderId()
        long linenumBefore = db.sequenceNextId("linenum")
        long ordernumBefore = db.sequenceNextId("ordernum")
        Map<String, Integer> qtyBefore = ["EST-1": db.inventoryQty("EST-1")]

        long orderId = placeOneOrder(["EST-1": 2])
        if (!db.orderExists(orderId)) {
            throw new IllegalStateException(
                    "R8a(order-detail-own)の前提が不成立: 参照予定のorderId=${orderId}がORDERSに存在しない。" +
                            "goldenは書き出さない。")
        }

        def response = http.get("/viewOrder.do?orderId=${orderId}")
        int httpStatus = response.statusCode()
        String html = response.body()
        List<Map<String, String>> lineRows = LegacyHtmlExtractor.extractOrderLineRows(html)
        String legacyProductNameBlank = lineRows.every { (it.productName as String).isBlank() }.toString()
        if (legacyProductNameBlank != "true") {
            throw new IllegalStateException(
                    "R8a(order-detail-own)のID-24前提が不成立: ViewOrder.jspのdescriptionセルが空でない" +
                            "(lineRows=${lineRows})。台帳のINTENDED_DIVERGENCE(ID-24)宣言が実測と食い違う。" +
                            "goldenは書き出さない。")
        }
        String orderTotal = LegacyHtmlExtractor.extractOrderTotal(html)

        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.httpStatus = httpStatus
        snapshot.orderTotal = orderTotal
        snapshot.lines = lineRows.collect { row ->
            new ParitySnapshot.Line(
                    itemId: row.itemId as String,
                    quantity: Integer.parseInt(row.quantity as String),
                    unitPrice: row.unitPrice as String,
                    productName: row.productName as String)
        }

        db.deleteOrdersAbove(maxOrderIdBefore)
        db.restoreSequenceNextId("ordernum", ordernumBefore)
        db.restoreSequenceNextId("linenum", linenumBefore)
        qtyBefore.each { String itemId, int qty -> db.restoreInventoryQty(itemId, qty) }

        return new CaptureResult(snapshot, [
                (orderId.toString()): [orderExists: true, legacyProductNameBlank: legacyProductNameBlank],
        ])
    }

    /**
     * R8b: 注文詳細（存在しないorderId）。旧{@code ViewOrderAction}は{@code getOrder()}のnull未チェックで
     * NPE→500＋スタックトレース露出（ID-14）。SM-3: この証拠をcanonical（{@code httpStatus}/
     * {@code stackTraceExposed}）として固定化する。
     */
    private CaptureResult orderDetailMissing() {
        http.resetSession()
        http.postForm("/signon.do", [username: "j2ee", password: "j2ee"])

        long missingOrderId = 999999999L
        if (db.orderExists(missingOrderId)) {
            throw new IllegalStateException(
                    "R8b(order-detail-missing)の前提が不成立: orderId=${missingOrderId}がORDERSに実在する。" +
                            "goldenは書き出さない。")
        }

        def response = http.get("/viewOrder.do?orderId=${missingOrderId}")
        int httpStatus = response.statusCode()
        String html = response.body()
        boolean stackTraceExposed = LegacyHtmlExtractor.containsStackTrace(html)
        if (httpStatus != 500 || !stackTraceExposed) {
            throw new IllegalStateException(
                    "R8b(order-detail-missing)のID-14前提が不成立: httpStatus=${httpStatus}, " +
                            "stackTraceExposed=${stackTraceExposed}(期待値=500/true)。goldenは書き出さない。")
        }

        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.httpStatus = httpStatus
        snapshot.stackTraceExposed = stackTraceExposed

        return new CaptureResult(snapshot, [
                (missingOrderId.toString()): [orderExists: false, legacyExceptionClass: "java.lang.NullPointerException"],
        ])
    }

    // ------------------------------------------------------------------
    // #51 T4: カート境界値（AC4・優先度は最後）
    // ------------------------------------------------------------------

    /**
     * cart-boundary: {@code Cart}/{@code CartItem}の未踏分岐を境界値で踏む。
     * {@code addItemToCart.do}を同一itemIdへ2回投げる（2回目は{@code AddItemToCartAction}が
     * {@code containsItemId}で分岐し{@code incrementQuantityByItemId}を呼ぶ経路。{@code Cart.addItem}自体は
     * 呼ばれない点に注意）→ {@code viewCart.do}でqty=2を確認 →
     * {@code removeItemFromCart.do}（1回目=removeItemByIdの非null側・在庫あり側）→
     * 再度{@code removeItemFromCart.do}（2回目=removeItemByIdのnull側・既に無い）→ 空のまま200。
     */
    private ParitySnapshot cartBoundary() {
        http.resetSession()
        http.postForm("/signon.do", [username: "j2ee", password: "j2ee"])

        http.get("/addItemToCart.do?workingItemId=EST-1")
        http.get("/addItemToCart.do?workingItemId=EST-1")

        String afterAddHtml = http.get("/viewCart.do").body()
        List<Map<String, String>> rowsAfterAdd = LegacyHtmlExtractor.extractCartRows(afterAddHtml)
        if (rowsAfterAdd != [[itemId: "EST-1", quantity: "2"]]) {
            throw new IllegalStateException(
                    "cart-boundaryの前提が不成立: 2回追加後のカート行=${rowsAfterAdd}(期待値=EST-1 qty=2)。" +
                            "goldenは書き出さない。")
        }

        String afterFirstRemoveHtml = http.get("/removeItemFromCart.do?workingItemId=EST-1").body()
        boolean emptyAfterFirstRemove = LegacyHtmlExtractor.isCartEmpty(afterFirstRemoveHtml)

        def secondRemoveResponse = http.get("/removeItemFromCart.do?workingItemId=EST-1")
        int httpStatusAfterSecondRemove = secondRemoveResponse.statusCode()
        boolean emptyAfterSecondRemove = LegacyHtmlExtractor.isCartEmpty(secondRemoveResponse.body())

        if (!emptyAfterFirstRemove || !emptyAfterSecondRemove || httpStatusAfterSecondRemove != 200) {
            throw new IllegalStateException(
                    "cart-boundaryの前提が不成立: emptyAfterFirstRemove=${emptyAfterFirstRemove}, " +
                            "emptyAfterSecondRemove=${emptyAfterSecondRemove}, " +
                            "httpStatusAfterSecondRemove=${httpStatusAfterSecondRemove}。goldenは書き出さない。")
        }

        ParitySnapshot snapshot = new ParitySnapshot()
        snapshot.entries = []
        return snapshot
    }

    /**
     * R7専用: 既にsignon済みのセッションで1件注文を確定し新しいorderIdを返す（後始末はしない・呼び出し元が
     * まとめて担当）。{@link #placeOrder}と異なり{@code resetSession}/{@code signon.do}を行わない
     * （複数回連続で呼ぶ用途のため。design.md §7.1と同じ駆動経路）。
     */
    private long placeOneOrder(Map<String, Integer> items) {
        long maxOrderIdBefore = db.maxOrderId()
        items.keySet().each { String itemId -> http.get("/addItemToCart.do?workingItemId=${itemId}") }
        Map<String, String> cartForm = items.collectEntries { itemId, qty -> [(itemId): qty.toString()] }
        http.postForm("/updateCartQuantities.do", cartForm)
        http.get("/checkout.do")
        http.get("/newOrderForm.do")
        http.postForm("/newOrder.do", newOrderFormFields())
        http.get("/newOrder.do?confirmed=true")

        long maxOrderIdAfter = db.maxOrderId()
        if (maxOrderIdAfter <= maxOrderIdBefore) {
            throw new IllegalStateException(
                    "placeOneOrder(items=${items})が失敗した: maxOrderIdBefore=${maxOrderIdBefore}, " +
                            "maxOrderIdAfter=${maxOrderIdAfter}(期待値: 増加)。")
        }
        return maxOrderIdAfter
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

    /**
     * GET駆動の一覧系ページを{@code page=next}が消えるまで辿り、各ページのHTMLから{@code extractor}で
     * 値を抽出して連結する（F5: legacyは4件/頁のため1頁目だけでは偽の不一致になる）。
     */
    private List walkPages(String firstPath, Closure extractor) {
        return walkPages(firstPath, null, extractor)
    }

    /** POST駆動（検索フォーム等）で1頁目を取得し、以降は{@code ?page=next}のGETで辿る。 */
    private List walkPages(String firstPath, Map<String, String> firstPostForm, Closure extractor) {
        String actionPath = firstPath.split(/\?/)[0]
        List all = []
        String html = firstPostForm ? http.postForm(firstPath, firstPostForm).body() : http.get(firstPath).body()
        all.addAll(extractor(html))
        while (LegacyHtmlExtractor.hasNextPage(html)) {
            html = http.get("${actionPath}?page=next").body()
            all.addAll(extractor(html))
        }
        return all
    }
}
