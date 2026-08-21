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

    // ---- #51 T2: 注文履歴照会(R7/R8a/R8b)の抽出 ----

    def "extractOrderListRowsはorderIdとtotalPriceを1行ずつ抽出する(#51 R7・ListOrders.jsp実物相当)"() {
        given: "ListOrders.jsp相当(;jsessionid=挿入込み・fmt:formatNumberが実機で生値を出す既知挙動)"
        String html = '''
            <table align="center" bgcolor="#008800" border="0" cellspacing="2" cellpadding="3">
              <tr bgcolor="#CCCCCC">  <td><b>Order ID</b></td>  <td><b>Date</b></td>  <td><b>Total Price</b></td>  </tr>
              <tr bgcolor="#FFFF88">
              <td><b><a href="/jpetstore/shop/viewOrder.do;jsessionid=ABC?orderId=1001">
                  <font color="BLACK">1001</font>
              </a></b></td>
              <td>2026/08/21 10:00:00</td>
              <td>16.5</td>
              </tr>
              <tr bgcolor="#FFFF88">
              <td><b><a href="/jpetstore/shop/viewOrder.do;jsessionid=ABC?orderId=1002">
                  <font color="BLACK">1002</font>
              </a></b></td>
              <td>2026/08/21 10:05:00</td>
              <td>$33.00</td>
              </tr>
            </table>
        '''

        expect:
        LegacyHtmlExtractor.extractOrderListRows(html) == [
                [orderId: "1001", totalPrice: "16.5"],
                [orderId: "1002", totalPrice: "33.00"],
        ]
    }

    def "extractOrderLineRowsはitemId/productName/quantity/unitPriceを抽出する(#51 R8a・ViewOrder.jsp実物相当)"() {
        given: "getLineItemsByOrderIdがLineItem.itemを埋めないため説明文セルは空白になる(ID-24の前提)"
        String html = '''
            <tr bgcolor="#FFFF88">
            <td><b><a href="/jpetstore/shop/viewItem.do;jsessionid=ABC?itemId=EST-1">
                <font color="BLACK">EST-1</font>
            </a></b></td>
            <td>


            </td>
            <td>2</td>
            <td align="right">16.5</td>
            <td align="right">33.0</td>
            </tr>
        '''

        expect:
        LegacyHtmlExtractor.extractOrderLineRows(html) == [
                [itemId: "EST-1", productName: "", quantity: "2", unitPrice: "16.5"],
        ]
    }

    def "extractOrderLineRowsは商品名セルが埋まっている場合はその文字列を返す(将来の回帰検知用)"() {
        given:
        String html = '''
            <tr bgcolor="#FFFF88">
            <td><b><a href="viewItem.do?itemId=EST-1">EST-1</a></b></td>
            <td>Large Angelfish</td>
            <td>2</td>
            <td align="right">$16.50</td>
            <td align="right">$33.00</td>
            </tr>
        '''

        expect:
        LegacyHtmlExtractor.extractOrderLineRows(html) ==
                [[itemId: "EST-1", productName: "Large Angelfish", quantity: "2", unitPrice: "16.50"]]
    }

    def "extractOrderLineRowsは明細行以外の(#付き住所等の)行を誤って拾わない(viewItem.doリンクの有無で判定)"() {
        given: "ViewOrder.jspの請求先住所行(itemIdリンクを含まない)"
        String html = '''
            <tr bgcolor="#FFFF88"><td>First name:</td><td>ABC</td></tr>
        '''

        expect:
        LegacyHtmlExtractor.extractOrderLineRows(html) == []
    }

    def "extractOrderTotalはTotal:に続く金額をドル記号・生値どちらでも抽出する(#51 R8a/R8b)"() {
        expect:
        LegacyHtmlExtractor.extractOrderTotal(
                '<td colspan="5" align="right"><b>Total: $33.00</b></td>') == "33.00"
        LegacyHtmlExtractor.extractOrderTotal(
                '<td colspan="5" align="right"><b>Total: 33.0</b></td>') == "33.0"
    }

    def "containsStackTraceは旧側の500エラーページ(NPEのスタックトレース)を検知する(SM-3・ID-14の証拠固定化)"() {
        expect:
        LegacyHtmlExtractor.containsStackTrace('''
            <h1>HTTP Status 500</h1>
            <p><b>type</b> Exception Report</p>
            <p><b>root cause</b></p>
            <pre>java.lang.NullPointerException
	at org.springframework.samples.jpetstore.web.struts.ViewOrderAction.doExecute(ViewOrderAction.java:18)
            </pre>
        ''')
        !LegacyHtmlExtractor.containsStackTrace('<html><body>ordinary page, no error here</body></html>')
    }

    // ---- #51 T4: カート境界値(cart-boundary) ----

    def "extractCartRowsはitemIdと数量入力欄の値を抽出する(#51 cart-boundary・Cart.jsp実物相当)"() {
        given:
        String html = '''
            <input type="text" size="3" name="EST-1" value="2" />
        '''

        expect:
        LegacyHtmlExtractor.extractCartRows(html) == [[itemId: "EST-1", quantity: "2"]]
    }

    def "isCartEmptyはCart.jspの空カート表示文言の有無で判定する"() {
        expect:
        LegacyHtmlExtractor.isCartEmpty(
                '<tr bgcolor="#FFFF88"><td colspan="8"><b>Your cart is empty.</b></td></tr>')
        !LegacyHtmlExtractor.isCartEmpty('<input type="text" size="3" name="EST-1" value="2" />')
    }

    def "extractCartSubTotalはSub Total:に続く金額を抽出する"() {
        expect:
        LegacyHtmlExtractor.extractCartSubTotal('<b>Sub Total: $33.00</b><br/>') == "33.00"
    }
}
