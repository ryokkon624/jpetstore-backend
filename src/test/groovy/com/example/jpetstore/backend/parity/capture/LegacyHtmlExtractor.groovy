package com.example.jpetstore.backend.parity.capture

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
}
