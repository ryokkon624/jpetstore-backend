package com.example.jpetstore.backend.presentation.rest

import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.infrastructure.security.AuthCookieSupport
import com.example.jpetstore.backend.infrastructure.security.JwtService
import com.example.jpetstore.backend.support.IntegrationTestBase
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Tag

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * #1 AC1/AC2/AC3/AC4: カタログREST API（category/product/item・ページング・在庫バッジ）のend-to-end実証。
 *
 * - AC4: 未認証でも200（読み取り専用・全公開）。非GETは405。
 * - AC2/ID-20: page/sizeパラメータ・1-index・既定size=12・size=2で多頁強制（DOGS6商品→3頁）。
 * - AC3: レスポンスJSONに qty/quantity フィールドが一切存在しない（在庫数非露出）。
 * - 論点5(#1最小範囲): 存在しないID→404（trace非露出）。
 */
@Tag("integration")
@AutoConfigureMockMvc
class CatalogControllerSpec extends IntegrationTestBase {

    @Autowired
    MockMvc mockMvc

    @Autowired
    JwtService jwtService

    private Cookie accessTokenCookie() {
        def user = new AuthenticatedUser(1L, "catalog_test_user", ["USER"])
        new Cookie(AuthCookieSupport.ACCESS_TOKEN_COOKIE, jwtService.generateAccessToken(user))
    }

    def "AC4: 未認証でGET /api/categoriesを叩くと200で[L2]の5件を返す"() {
        expect:
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.length()').value(5))
                .andExpect(jsonPath('$[0].categoryId').value("BIRDS"))
    }

    def "AC4: 未認証でGET /api/categories/{id}を叩くと200でカテゴリ詳細を返す"() {
        expect:
        mockMvc.perform(get("/api/categories/DOGS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.categoryId').value("DOGS"))
                .andExpect(jsonPath('$.name').value("Dogs"))
    }

    def "AC2/ID-20: size=2でGET /api/categories/DOGS/productsを叩くと1頁目2件・totalPages=3を返す(1-index)"() {
        expect:
        mockMvc.perform(get("/api/categories/DOGS/products?size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.content.length()').value(2))
                .andExpect(jsonPath('$.page').value(1))
                .andExpect(jsonPath('$.size').value(2))
                .andExpect(jsonPath('$.totalElements').value(6))
                .andExpect(jsonPath('$.totalPages').value(3))
    }

    def "AC2: sizeを省略すると既定12件でDOGSの6件すべてが1頁で返る"() {
        expect:
        mockMvc.perform(get("/api/categories/DOGS/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.content.length()').value(6))
                .andExpect(jsonPath('$.size').value(12))
                .andExpect(jsonPath('$.totalPages').value(1))
    }

    def "AC4: 未認証でGET /api/products/{id}を叩くと200で商品詳細を返す"() {
        expect:
        mockMvc.perform(get("/api/products/K9-RT-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.productId').value("K9-RT-02"))
                .andExpect(jsonPath('$.categoryId').value("DOGS"))
    }

    def "AC2: GET /api/products/K9-RT-02/itemsは最多4件をtotalElementsで返す"() {
        expect:
        mockMvc.perform(get("/api/products/K9-RT-02/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.content.length()').value(4))
                .andExpect(jsonPath('$.totalElements').value(4))
    }

    def "AC3: GET /api/products/FI-SW-01/itemsのレスポンスJSONにqty/quantityフィールドが存在しない"() {
        when:
        def result = mockMvc.perform(get("/api/products/FI-SW-01/items"))
                .andExpect(status().isOk())
                .andReturn()

        then:
        def body = result.response.contentAsString.toLowerCase()
        !body.contains("qty")
        !body.contains("quantity")
    }

    def "AC3: 残少アイテム(EST-2, qty=1)のitemsレスポンスはstockStatus=LOW_STOCKになる"() {
        expect:
        mockMvc.perform(get("/api/products/FI-SW-01/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.content[?(@.itemId == "EST-2")].stockStatus').value("LOW_STOCK"))
    }

    def "AC3: GET /api/items/{id}は在庫status(OUT_STOCK)を返しqty/quantityフィールドを含まない"() {
        when:
        def result = mockMvc.perform(get("/api/items/EST-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.itemId').value("EST-3"))
                .andExpect(jsonPath('$.productId').value("FI-SW-02"))
                .andExpect(jsonPath('$.stockStatus').value("OUT_STOCK"))
                .andReturn()

        then:
        def body = result.response.contentAsString.toLowerCase()
        !body.contains("qty")
        !body.contains("quantity")
    }

    def "AC1: GET /api/items/{id}は商品名(product.getProduct()相当)を併せ持つ"() {
        expect:
        mockMvc.perform(get("/api/items/EST-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.productName').value("Labrador Retriever"))
    }

    def "論点5: 存在しないcategoryIdは404に正規化される(trace非露出)"() {
        expect:
        mockMvc.perform(get("/api/categories/NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath('$.code').value("NOT_FOUND"))
    }

    def "論点5: 存在しないcategoryIdのproducts一覧も404になる(親ID存在チェック)"() {
        expect:
        mockMvc.perform(get("/api/categories/NOPE/products"))
                .andExpect(status().isNotFound())
    }

    def "論点5: 存在しないproductIdは404に正規化される"() {
        expect:
        mockMvc.perform(get("/api/products/NOPE"))
                .andExpect(status().isNotFound())
    }

    def "論点5: 存在しないitemIdは404に正規化される"() {
        expect:
        mockMvc.perform(get("/api/items/NOPE"))
                .andExpect(status().isNotFound())
    }

    def "AC4: 認証済み+CSRF有でもPOST /api/categoriesは405になる(GETのみ・状態変更不可)"() {
        // 未認証+CSRF無しのPOSTはSecurityの認可/CSRF段で403/401になり、Spring MVCの
        // メソッド不一致判定(405)まで到達しない。認証済み+CSRF有の状態でもなお405であることを
        // 示すことで「そもそもPOSTを受理するハンドラが存在しない」ことをより強く実証する。
        expect:
        mockMvc.perform(post("/api/categories").with(csrf()).cookie(accessTokenCookie()))
                .andExpect(status().isMethodNotAllowed())
    }

    def "AC4: 認証済み+CSRF有でもPOST /api/items/EST-1は405になる(状態変更不可)"() {
        expect:
        mockMvc.perform(post("/api/items/EST-1").with(csrf()).cookie(accessTokenCookie()))
                .andExpect(status().isMethodNotAllowed())
    }

    def "AC4: 未認証+CSRF無しのPOST /api/categoriesは403で拒否される(state-changing不可)"() {
        expect:
        mockMvc.perform(post("/api/categories"))
                .andExpect(status().isForbidden())
    }

    def "#3 AC-neg1: 非数値pageは400に正規化される(500でない)"() {
        expect:
        mockMvc.perform(get("/api/categories/DOGS/products?page=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath('$.code').value("BAD_REQUEST"))
    }

    def "#3 AC-neg1: 非数値sizeは400に正規化される(500でない)"() {
        expect:
        mockMvc.perform(get("/api/categories/DOGS/products?size=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath('$.code').value("BAD_REQUEST"))
    }

    def "#3 AC-neg1: 桁あふれのpage(Integer範囲超)は400に正規化される(500でない)"() {
        expect:
        mockMvc.perform(get("/api/categories/DOGS/products?page=99999999999999999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath('$.code').value("BAD_REQUEST"))
    }

    def "#3 AC-neg1: stale頁送り相当(範囲外の有効なpage番号)はクランプされ空200を返す(500でない)"() {
        expect:
        mockMvc.perform(get("/api/categories/DOGS/products?page=9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.content.length()').value(0))
                .andExpect(jsonPath('$.totalElements').value(6))
    }

    def "#3 AC-neg1: 未知のサブパスは404に正規化される(500でない・NoResourceFoundException)"() {
        expect:
        mockMvc.perform(get("/api/categories/DOGS/unknown-subpath"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath('$.code').value("NOT_FOUND"))
    }
}
