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
