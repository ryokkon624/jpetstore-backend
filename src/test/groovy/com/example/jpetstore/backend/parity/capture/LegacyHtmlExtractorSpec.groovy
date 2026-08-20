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

    def "extractSelectOptionsはselect内のoption値を出現順で抽出する(#49 R1・IncludeAccountFields.jsp相当)"() {
        given: "NewAccountForm.jsp相当(languagesセレクトと紛れないよう対象selectのみ拾う)"
        String html = '''
            <select name="workingAccountForm" property="account.languagePreference">
              <option value="english">English</option>
            </select>
            <select name="account.favouriteCategoryId">
              <option value="FISH">Fish</option>
              <option value="DOGS">Dogs</option>
              <option value="REPTILES">Reptiles</option>
              <option value="CATS">Cats</option>
              <option value="BIRDS">Birds</option>
            </select>
        '''

        expect:
        LegacyHtmlExtractor.extractSelectOptions(html, "account.favouriteCategoryId") ==
                ["FISH", "DOGS", "REPTILES", "CATS", "BIRDS"]
    }

    def "extractSelectOptionsは対象selectが無ければ空リストを返す"() {
        expect:
        LegacyHtmlExtractor.extractSelectOptions("<p>no select here</p>", "account.favouriteCategoryId") == []
    }

    def "extractItemRowsはitemIdとlistPriceを1行ずつ抽出する(#49 R3・Product.jsp実機の生の数値出力)"() {
        given: "Product.jsp相当の行(実機ではfmt:formatNumberが効かずドル記号無し・末尾ゼロ無しの生数値になる＝実測)"
        String html = '''
            <tr bgcolor="#FFFF88">
            <td><b>
            <a href="/jpetstore/shop/viewItem.do;jsessionid=ABC?itemId=EST-1">
              EST-1
            </a></b></td>
            <td>FI-SW-01</td>
            <td>Large Angelfish</td>
            <td>16.5</td>
            <td><a href="/jpetstore/shop/addItemToCart.do?workingItemId=EST-1"><img/></a></td>
            </tr>
        '''

        expect:
        LegacyHtmlExtractor.extractItemRows(html) == [[itemId: "EST-1", listPrice: "16.5"]]
    }

    def "extractItemRowsは説明文セルの数字混じりテキストを誤って価格と判定しない(セル内容が数値のみの場合だけ拾う)"() {
        given: "説明文セルに数字が混ざっていても、独立した<td>数値</td>のセルのみを価格として拾う"
        String html = '''
            <tr bgcolor="#FFFF88">
            <td><a href="viewItem.do?itemId=EST-9">EST-9</a></td>
            <td>Model 007 Special</td>
            <td>93.5</td>
            </tr>
        '''

        expect:
        LegacyHtmlExtractor.extractItemRows(html) == [[itemId: "EST-9", listPrice: "93.5"]]
    }
}
