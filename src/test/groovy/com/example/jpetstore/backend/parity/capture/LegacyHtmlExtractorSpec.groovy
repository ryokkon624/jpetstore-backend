package com.example.jpetstore.backend.parity.capture

import spock.lang.Specification

/**
 * {@link LegacyHtmlExtractor} のUT（#48 AC6・design.md §7.1の実測regexが機能することを実証）。
 * Docker不要。{@code ;jsessionid=} 挿入込みのHTML断片をinlineで与える（legacyを起動しない）。
 */
class LegacyHtmlExtractorSpec extends Specification {

    // fish.html（spike実測・viewCategory.do?categoryId=FISH応答）を模したproductId行の抜粋。
    private static final String CATEGORY_FRAGMENT = '''
        <tr bgcolor="#FFFF88">
        <td><b><a href="/jpetstore/shop/viewProduct.do;jsessionid=FC6FDFF0A465887432A02699B26BA179?productId=FI-FW-01">
            <font color="BLACK">FI-FW-01</font>
          </a></b></td>
        <td>Koi</td>
        </tr>
        <tr bgcolor="#FFFF88">
        <td><b><a href="/jpetstore/shop/viewProduct.do;jsessionid=FC6FDFF0A465887432A02699B26BA179?productId=FI-SW-01">
            <font color="BLACK">FI-SW-01</font>
          </a></b></td>
        <td>Angelfish</td>
        </tr>
    '''

    def "productIdを;jsessionid=挿入込みのURLから抽出できる(spike実測regex)"() {
        expect:
        LegacyHtmlExtractor.extractProductIds(CATEGORY_FRAGMENT) == ["FI-FW-01", "FI-SW-01"]
    }

    def "categoryIdを;jsessionid=挿入込みのURLから抽出でき、複数回出現しても初出順で重複除去される"() {
        given: "index.jsp相当(トップナビ+サイドバーで同じcategoryIdが2回リンクされる)"
        String html = '''
            <a href="/jpetstore/shop/viewCategory.do;jsessionid=ABC?categoryId=FISH">
            <a href="/jpetstore/shop/viewCategory.do;jsessionid=ABC?categoryId=DOGS">
            <a href="/jpetstore/shop/viewCategory.do;jsessionid=ABC?categoryId=FISH">
        '''

        expect:
        LegacyHtmlExtractor.extractCategoryIds(html) == ["FISH", "DOGS"]
    }

    def ";jsessionid=が無いURL(未ログイン等)でも抽出できる"() {
        given:
        String html = '<a href="/jpetstore/shop/viewProduct.do?productId=K9-BD-01">Bulldog</a>'

        expect:
        LegacyHtmlExtractor.extractProductIds(html) == ["K9-BD-01"]
    }

    def "itemIdをviewItem.doのURLから抽出できる"() {
        given:
        String html = '<a href="viewItem.do;jsessionid=XYZ?itemId=EST-1">EST-1</a>'

        expect:
        LegacyHtmlExtractor.extractItemIds(html) == ["EST-1"]
    }

    def "hasNextPageはpage=nextリンクの有無で判定する(F5の停止条件)"() {
        expect:
        LegacyHtmlExtractor.hasNextPage('<a href="viewCategory.do?categoryId=FISH&page=next">Next</a>')
        !LegacyHtmlExtractor.hasNextPage('<p>no more pages</p>')
    }
}
