package com.example.jpetstore.backend.parity.capture

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * legacy（Struts/JSP）のHTML応答からcanonical値を抽出するユーティリティ（#48 AC6・design.md §7.1）。
 *
 * <p>legacyはStruts標準の {@code ;jsessionid=...} をURLパスとクエリの間に挿入する
 * （例: {@code viewProduct.do;jsessionid=XXXX?productId=FI-SW-01}）。抽出対象のアクション名と
 * クエリ文字列の間に任意の {@code ;jsessionid=...} が挟まる前提で正規表現を組む
 * （spike §7.1で実証済み・推測で上書きしない）。
 */
class LegacyHtmlExtractor {

    /**
     * {@code <action>[;jsessionid=...]?<param>=<value>} の形から {@code <value>} を抽出する。
     * 同一IDが複数回リンクされる場合（サイドバー＋メインコンテンツ等）は初出順を保ったまま重複除去する。
     */
    static List<String> extractIds(String html, String action, String param) {
        Pattern pattern = Pattern.compile(
                "${Pattern.quote(action)}[^?]*\\?${Pattern.quote(param)}=([A-Za-z0-9._%-]+)")
        List<String> ids = []
        def matcher = pattern.matcher(html)
        while (matcher.find()) {
            String id = matcher.group(1)
            if (!ids.contains(id)) {
                ids << id
            }
        }
        return ids
    }

    static List<String> extractCategoryIds(String html) {
        return extractIds(html, "viewCategory.do", "categoryId")
    }

    static List<String> extractProductIds(String html) {
        return extractIds(html, "viewProduct.do", "productId")
    }

    static List<String> extractItemIds(String html) {
        return extractIds(html, "viewItem.do", "itemId")
    }

    /**
     * ページ送りリンク（Next）の有無。{@code Category.jsp}/{@code SearchProducts.jsp} は
     * {@code !lastPage} のときだけ {@code page=next} リンクを出す（design.md F5・#49 AC2の全ページ走査の停止条件）。
     */
    static boolean hasNextPage(String html) {
        return html.contains("page=next")
    }

    /**
     * {@code <select name="...">...</select>} 内の {@code <option value="...">} を出現順で抽出する
     * （#49 R1: {@code NewAccountForm.jsp} の {@code account.favouriteCategoryId} セレクト＝
     * 動的な {@code getCategoryList()} 呼び出し経路）。
     */
    static List<String> extractSelectOptions(String html, String selectName) {
        Pattern selectPattern = Pattern.compile(
                "<select[^>]*name=[\"']${Pattern.quote(selectName)}[\"'][^>]*>(.*?)</select>",
                Pattern.DOTALL)
        Matcher selectMatcher = selectPattern.matcher(html)
        if (!selectMatcher.find()) {
            return []
        }
        String body = selectMatcher.group(1)
        Pattern optionPattern = Pattern.compile("<option\\s+value=[\"']([^\"']*)[\"']")
        List<String> values = []
        Matcher optionMatcher = optionPattern.matcher(body)
        while (optionMatcher.find()) {
            values << optionMatcher.group(1)
        }
        return values
    }

    /**
     * {@code Product.jsp}/{@code Cart.jsp}系の行（{@code <tr bgcolor="#FFFF88">...</tr>}）から
     * itemId＋listPriceを1行ずつ抽出する（#49 R3）。
     *
     * <p>JSPソースは{@code <fmt:formatNumber value="${item.listPrice}" pattern="$#,##0.00"/>}
     * だが、**実機（本コンテナのデプロイ）ではこのタグがフォーマットを適用せず、生の数値
     * （例: {@code 16.5}。{@code $}無し・末尾ゼロ無し）がそのまま出力される**ことを実測で確認した
     * （legacy側の既存の(意図せぬ)挙動であり本ツールでは変更しない。W1のSub Total表示も同様に
     * "33.0"と出る）。そのため{@code <td>数値</td>}という「セル内容が数値のみ」の形で価格セルを
     * 識別する（説明文セルは非数値のため誤マッチしない）。
     */
    static List<Map<String, String>> extractItemRows(String html) {
        List<Map<String, String>> rows = []
        Pattern rowPattern = Pattern.compile("<tr bgcolor=\"#FFFF88\">(.*?)</tr>", Pattern.DOTALL)
        Matcher rowMatcher = rowPattern.matcher(html)
        while (rowMatcher.find()) {
            String block = rowMatcher.group(1)
            List<String> itemIds = extractItemIds(block)
            if (itemIds.isEmpty()) {
                continue
            }
            String price = extractPlainDecimal(block)
            if (price == null) {
                continue
            }
            rows << [itemId: itemIds[0], listPrice: price]
        }
        return rows
    }

    /**
     * {@code <td>数値</td>}（前後の空白のみ許容・タグ混在無し）という「セル内容が数値のみ」の
     * 最初の出現を抽出する。{@code fmt:formatNumber}が実質フォーマットを適用しない実機挙動の
     * 価格セル抽出に使う（{@link #extractItemRows}・R4のlistPrice抽出で共用）。
     */
    static String extractPlainDecimal(String html) {
        Matcher matcher = Pattern.compile("<td>\\s*([0-9]+(?:\\.[0-9]+)?)\\s*</td>").matcher(html)
        return matcher.find() ? matcher.group(1) : null
    }

    // ------------------------------------------------------------------
    // #51 T2: 注文履歴照会(R7/R8a/R8b)
    // ------------------------------------------------------------------

    /**
     * {@code ListOrders.jsp} の1行（orderId＋totalPrice）を抽出する（R7）。{@code viewOrder.do?orderId=}の
     * リンクを含む行のみを対象にし、ヘッダ行（{@code #CCCCCC}）は{@link #extractIds}が空を返すため自然に除外される。
     * 金額は{@code $}・{@code ,}を許容するトレラントparserで剥がす（実機で{@code fmt:formatNumber}が
     * フォーマットを適用しない既知挙動・R4と同様）。
     */
    static List<Map<String, String>> extractOrderListRows(String html) {
        List<Map<String, String>> rows = []
        Matcher rowMatcher = Pattern.compile("<tr bgcolor=\"#FFFF88\">(.*?)</tr>", Pattern.DOTALL).matcher(html)
        while (rowMatcher.find()) {
            String block = rowMatcher.group(1)
            List<String> orderIds = extractIds(block, "viewOrder.do", "orderId")
            if (orderIds.isEmpty()) {
                continue
            }
            List<String> cells = extractCellTexts(block)
            if (cells.size() < 3) {
                continue
            }
            String totalPrice = extractMoney(cells[2])
            if (totalPrice == null) {
                continue
            }
            rows << [orderId: orderIds[0], totalPrice: totalPrice]
        }
        return rows
    }

    /**
     * {@code ViewOrder.jsp} の明細行（itemId／productName／quantity／unitPrice）を抽出する（R8a/R8b）。
     * {@code viewItem.do?itemId=}のリンクを含む行のみを対象にする（請求先/配送先住所の行や合計行を誤って
     * 拾わない。{@link #extractItemRows}と同じフィルタ方式）。
     *
     * <p>{@code productName}はdescriptionセル（{@code attribute1..5}＋{@code product.name}の連結）を
     * そのまま返す。{@code dao/ibatis/maps/LineItem.xml}の{@code getLineItemsByOrderId}が
     * {@code LineItem.item}を一切埋めないため、通常は空文字になる（Q6・ID-24の観測点）。
     */
    static List<Map<String, String>> extractOrderLineRows(String html) {
        List<Map<String, String>> rows = []
        Matcher rowMatcher = Pattern.compile("<tr bgcolor=\"#FFFF88\">(.*?)</tr>", Pattern.DOTALL).matcher(html)
        while (rowMatcher.find()) {
            String block = rowMatcher.group(1)
            List<String> itemIds = extractItemIds(block)
            if (itemIds.isEmpty()) {
                continue
            }
            List<String> cells = extractCellTexts(block)
            if (cells.size() < 4) {
                continue
            }
            String unitPrice = extractMoney(cells[3])
            if (unitPrice == null) {
                continue
            }
            String productName = stripTags(cells[1]).trim()
            String quantity = stripTags(cells[2]).trim()
            rows << [itemId: itemIds[0], productName: productName, quantity: quantity, unitPrice: unitPrice]
        }
        return rows
    }

    /**
     * {@code ViewOrder.jsp}の{@code Total: <fmt:formatNumber .../>}から合計金額を抽出する（R8a/R8b）。
     * 金額は{@code $}・{@code ,}を許容するトレラントparser。
     */
    static String extractOrderTotal(String html) {
        Matcher matcher = Pattern.compile(/Total:\s*\$?\s*([0-9][0-9,]*(?:\.[0-9]+)?)/).matcher(html)
        return matcher.find() ? matcher.group(1).replace(",", "") : null
    }

    /**
     * 旧の500エラーページ本文にスタックトレースが露出しているかを判定する（R8b・SM-3・ID-14の証拠固定化）。
     * {@code web.xml}に{@code error-page}が無く{@code struts-config.xml}に{@code global-exceptions}も
     * 無いため、{@code ViewOrderAction}のNPEはコンテナ既定のエラーページ（例外クラス名＋スタックトレース）
     * としてそのまま露出する。特定のコンテナ実装のHTML書式に依存しないよう、例外クラス名・本プロジェクトの
     * パッケージ名を含むフレーム・典型的な見出し文言のいずれかで判定する（複数指標のOR）。
     */
    static boolean containsStackTrace(String html) {
        return html.contains("NullPointerException") ||
                html.contains("at org.springframework.samples.jpetstore") ||
                html.contains("root cause") ||
                html.contains("Exception Report")
    }

    // ------------------------------------------------------------------
    // #51 T4: カート境界値(cart-boundary)
    // ------------------------------------------------------------------

    /**
     * {@code Cart.jsp}の数量入力欄（{@code <input type="text" size="3" name="&lt;itemId&gt;"
     * value="&lt;quantity&gt;">}）からitemId/quantityを抽出する。
     */
    static List<Map<String, String>> extractCartRows(String html) {
        List<Map<String, String>> rows = []
        Matcher matcher = Pattern.compile(
                "<input type=\"text\" size=\"3\" name=\"([A-Za-z0-9-]+)\" value=\"([0-9]+)\"").matcher(html)
        while (matcher.find()) {
            rows << [itemId: matcher.group(1), quantity: matcher.group(2)]
        }
        return rows
    }

    /** {@code Cart.jsp}の空カート表示文言（{@code cartForm.cart.numberOfItems == 0}分岐）の有無。 */
    static boolean isCartEmpty(String html) {
        return html.contains("Your cart is empty.")
    }

    /** {@code Cart.jsp}の{@code Sub Total: <fmt:formatNumber .../>}から小計を抽出する。 */
    static String extractCartSubTotal(String html) {
        Matcher matcher = Pattern.compile(/Sub Total:\s*\$?\s*([0-9][0-9,]*(?:\.[0-9]+)?)/).matcher(html)
        return matcher.find() ? matcher.group(1).replace(",", "") : null
    }

    // ------------------------------------------------------------------
    // 内部ヘルパー
    // ------------------------------------------------------------------

    /** {@code <tr>}ブロック内の{@code <td>...</td>}セル本文を出現順で返す（ネストしたtdは想定しない）。 */
    private static List<String> extractCellTexts(String block) {
        List<String> cells = []
        Matcher matcher = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL).matcher(block)
        while (matcher.find()) {
            cells << matcher.group(1)
        }
        return cells
    }

    /** セル本文から{@code $}・{@code ,}を剥がした金額文字列を抽出する（実機の生値/フォーマット値どちらにも対応）。 */
    private static String extractMoney(String cellHtml) {
        Matcher matcher = Pattern.compile(/\$?\s*([0-9][0-9,]*(?:\.[0-9]+)?)/).matcher(cellHtml)
        return matcher.find() ? matcher.group(1).replace(",", "") : null
    }

    /** セル本文からHTMLタグを取り除く（description セルにタグは通常混在しないが防御的に処理する）。 */
    private static String stripTags(String html) {
        return html.replaceAll("<[^>]+>", "")
    }
}
