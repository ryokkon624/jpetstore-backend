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
}
