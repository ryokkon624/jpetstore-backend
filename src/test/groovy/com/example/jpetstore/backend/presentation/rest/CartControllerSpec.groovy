package com.example.jpetstore.backend.presentation.rest

import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.infrastructure.security.AuthCookieSupport
import com.example.jpetstore.backend.infrastructure.security.JwtService
import com.example.jpetstore.backend.support.IntegrationTestBase
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Tag

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * #4 AC1〜AC5・[L2]・AC-neg1: カートREST API（/api/cart）のend-to-end実証。
 *
 * - AC1: 追加/数量更新/削除/表示（明示的な{itemId,quantity} API）。
 * - AC2: 数量0以下は行削除（単一削除経路・幽霊行=ID-17を踏襲しない）。
 * - AC4/SBD-10: 未認証は401、未知itemIdは404。
 * - AC5/AC-neg1: 在庫切れ追加不可・数量上限=在庫数をserver強制（400）。qty自体は非露出（ID-28）。
 * - [L2]: 小計はΣ(listPrice×quantity)のサーバ計算。
 * - D1: orderable EP（GET /api/items/{itemId}/orderable）は未認証でも到達可能。
 */
@Tag("integration")
@AutoConfigureMockMvc
class CartControllerSpec extends IntegrationTestBase {

    private static final String USERNAME = "cart_controller_test_user"

    @Autowired
    MockMvc mockMvc

    @Autowired
    JwtService jwtService

    @Autowired
    JdbcTemplate jdbcTemplate

    Long userId
    Cookie accessTokenCookie

    void setup() {
        jdbcTemplate.update("DELETE FROM t_cart_item WHERE cart_id IN (SELECT cart_id FROM t_cart WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?))", USERNAME)
        jdbcTemplate.update("DELETE FROM t_cart WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)", USERNAME)
        jdbcTemplate.update("DELETE FROM m_account WHERE username = ?", USERNAME)
        jdbcTemplate.update(
                """
                INSERT INTO m_account
                    (username, email, first_name, last_name, status, address1, city, state, postal_code, country, phone,
                     create_program, update_program)
                VALUES (?, ?, 'Cart', 'ControllerTestUser', 'OK', '1 Test St', 'Testville', 'CA', '90000', 'USA', '555-0100',
                     'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                USERNAME, "${USERNAME}@example.com")
        userId = jdbcTemplate.queryForObject("SELECT user_id FROM m_account WHERE username = ?", Long, USERNAME)
        def user = new AuthenticatedUser(userId, USERNAME, ["USER"])
        accessTokenCookie = new Cookie(AuthCookieSupport.ACCESS_TOKEN_COOKIE, jwtService.generateAccessToken(user))
    }

    def "AC4: 未認証でGET /api/cartを叩くと401になる"() {
        expect:
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
    }

    def "認証済みでGET /api/cartを叩くと200で空カート(items=[],subtotal=0)を返す(初回はensureCartで自動作成)"() {
        expect:
        mockMvc.perform(get("/api/cart").cookie(accessTokenCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items.length()').value(0))
                .andExpect(jsonPath('$.subtotal').value(0))
    }

    def "AC1: 認証済み+CSRFでPOST /api/cart/itemsに追加すると200でカートに反映される"() {
        expect:
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-1","quantity":2}'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items.length()').value(1))
                .andExpect(jsonPath('$.items[0].itemId').value("EST-1"))
                .andExpect(jsonPath('$.items[0].quantity').value(2))
                .andExpect(jsonPath('$.items[0].stockStatus').value("IN_STOCK"))
    }

    def "AC1: quantity省略時は既定1で追加される"() {
        expect:
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-1"}'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items[0].quantity').value(1))
    }

    def "AC1: 同一アイテムに2回追加すると数量が加算される(legacyの+1挙動を一般化)"() {
        given:
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-1","quantity":2}'))
                .andExpect(status().isOk())

        expect:
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-1","quantity":3}'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items[0].quantity').value(5))
    }

    def "SBD-2(sec指摘): POST /api/cart/itemsでquantity=#quantityは400になり永続化されない"() {
        expect:
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"itemId":"EST-1","quantity":${quantity}}"""))
                .andExpect(status().isBadRequest())

        and: "カートは空のまま(永続化されていない)"
        mockMvc.perform(get("/api/cart").cookie(accessTokenCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items.length()').value(0))

        where:
        quantity << [0, -1, -100]
    }

    def "SBD-2(sec指摘): intオーバーフローする加算要求は400になり、既存の正しい数量が負値で上書きされない"() {
        given: "既存数量1を作る"
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-1","quantity":1}'))
                .andExpect(status().isOk())

        expect: "Integer.MAX_VALUEを追加するとintがオーバーフローし400で拒否される"
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-1","quantity":2147483647}'))
                .andExpect(status().isBadRequest())

        and: "既存の数量1は書き換わらず維持されている(負値で汚染されない)"
        mockMvc.perform(get("/api/cart").cookie(accessTokenCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items[0].quantity').value(1))
    }

    def "AC5: 在庫切れアイテム(EST-3, stock=0)の追加は400になる"() {
        expect:
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-3","quantity":1}'))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath('$.code').value("BAD_REQUEST"))
    }

    def "AC-neg1: 在庫数を超える追加は400になる(EST-2, stock=1に2個要求)"() {
        expect:
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-2","quantity":2}'))
                .andExpect(status().isBadRequest())
    }

    def "AC4/SBD-10: 存在しないitemIdの追加は404になる"() {
        expect:
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"NOPE","quantity":1}'))
                .andExpect(status().isNotFound())
    }

    def "AC1: PUT /api/cart/items/{itemId}で数量を絶対値更新できる"() {
        given:
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-1","quantity":2}'))
                .andExpect(status().isOk())

        expect:
        mockMvc.perform(put("/api/cart/items/EST-1").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"quantity":7}'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items[0].quantity').value(7))
    }

    def "AC2: PUTでquantity=0にすると行削除される(単一削除経路)"() {
        given:
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-1","quantity":2}'))
                .andExpect(status().isOk())

        expect:
        mockMvc.perform(put("/api/cart/items/EST-1").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"quantity":0}'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items.length()').value(0))
    }

    def "AC1: DELETE /api/cart/items/{itemId}で明示削除できる"() {
        given:
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-1","quantity":2}'))
                .andExpect(status().isOk())

        expect:
        mockMvc.perform(delete("/api/cart/items/EST-1").with(csrf()).cookie(accessTokenCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items.length()').value(0))
    }

    def "[L2]: 小計はΣ(listPrice×quantity)のサーバ計算になる(EST-1×2+EST-22×1=16.50*2+135.50*1=168.50)"() {
        given:
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-1","quantity":2}'))
                .andExpect(status().isOk())
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-22","quantity":1}'))
                .andExpect(status().isOk())

        expect:
        mockMvc.perform(get("/api/cart").cookie(accessTokenCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.subtotal').value(168.50d))
    }

    def "#5 AC-neg1: 価格系フィールド(listPrice/lineTotal/productName)を注入しても無視されサーバのマスター値になる(SBD-2)"() {
        expect:
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-1","quantity":2,"listPrice":0.01,"lineTotal":0.01,"productName":"HACKED"}'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items[0].listPrice').value(16.50d))
                .andExpect(jsonPath('$.items[0].productName').value("Angelfish"))
                .andExpect(jsonPath('$.subtotal').value(33.00d))
    }

    def "#5 AC2: PUT /api/cart/items/{itemId}でquantity=-1は400になり永続化されない(負数の明示拒否)"() {
        given:
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-1","quantity":2}'))
                .andExpect(status().isOk())

        expect:
        mockMvc.perform(put("/api/cart/items/EST-1").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"quantity":-1}'))
                .andExpect(status().isBadRequest())

        and: "既存数量2は書き換わらない"
        mockMvc.perform(get("/api/cart").cookie(accessTokenCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items[0].quantity').value(2))
    }

    def "#5 AC2: PUT /api/cart/items/{itemId}でquantity欠落は400になる(@NotNull)"() {
        expect:
        mockMvc.perform(put("/api/cart/items/EST-1").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{}'))
                .andExpect(status().isBadRequest())
    }

    def "#5 AC2: PUT /api/cart/items/{itemId}でquantityが非数値は400になる(HttpMessageNotReadableException正規化)"() {
        expect:
        mockMvc.perform(put("/api/cart/items/EST-1").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"quantity":"abc"}'))
                .andExpect(status().isBadRequest())
    }

    def "#5 AC2: PUTでquantity=0は引き続き行削除になる(確定事項②・0=削除セマンティクスの温存回帰)"() {
        given:
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-1","quantity":2}'))
                .andExpect(status().isOk())

        expect:
        mockMvc.perform(put("/api/cart/items/EST-1").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"quantity":0}'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items.length()').value(0))
    }

    def "#5 AC2: POST /api/cart/mergeでquantity<=0(#quantity)の行は400になる(黙殺廃止)"() {
        expect:
        mockMvc.perform(post("/api/cart/merge").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""[{"itemId":"EST-1","quantity":${quantity}}]"""))
                .andExpect(status().isBadRequest())

        where:
        quantity << [0, -1]
    }

    def "#6 AC-neg1: XSRF-TOKEN Cookieの値とX-XSRF-TOKENヘッダの値が不一致だと403になる(攻撃者はCookie値を読めず正しいヘッダを付与できない状況の再現・double-submitの照合失敗)"() {
        expect:
        mockMvc.perform(post("/api/cart/items")
                .cookie(accessTokenCookie, new Cookie("XSRF-TOKEN", "legit-looking-token-attacker-cannot-forge"))
                .header("X-XSRF-TOKEN", "attacker-cannot-know-this-value")
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-1","quantity":1}'))
                .andExpect(status().isForbidden())
    }

    def "#6 AC-neg1: 外部オリジンを装いOriginヘッダのみ付けてCSRFトークン無しで送ると403になる(GETでのカート変更リンクも存在しない=非GETのみが状態変更経路)"() {
        expect:
        mockMvc.perform(post("/api/cart/items").cookie(accessTokenCookie)
                .header("Origin", "https://evil.example.com")
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-1","quantity":1}'))
                .andExpect(status().isForbidden())
    }

    def "#6 AC2: PUTで同じ数量を2回送っても同じ結果になる(冪等)"() {
        given:
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-1","quantity":3}'))
                .andExpect(status().isOk())

        when:
        def first = mockMvc.perform(put("/api/cart/items/EST-1").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"quantity":5}'))
                .andExpect(status().isOk())
                .andReturn()
        def second = mockMvc.perform(put("/api/cart/items/EST-1").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"quantity":5}'))
                .andExpect(status().isOk())
                .andReturn()

        then:
        first.response.contentAsString == second.response.contentAsString
    }

    def "#6 AC2: DELETEを2回送っても2回目も200かつ空カートのまま(冪等)"() {
        given:
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-1","quantity":2}'))
                .andExpect(status().isOk())

        expect: "1回目の削除"
        mockMvc.perform(delete("/api/cart/items/EST-1").with(csrf()).cookie(accessTokenCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items.length()').value(0))

        and: "2回目も200かつ空のまま(冪等)"
        mockMvc.perform(delete("/api/cart/items/EST-1").with(csrf()).cookie(accessTokenCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items.length()').value(0))
    }

    def "計画②: POST /api/cart/mergeはclient行をサーバ側に加算し在庫数でクランプする"() {
        given: "サーバ側にEST-1を3個持つ状態"
        mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-1","quantity":3}'))
                .andExpect(status().isOk())

        expect: "client側2個をマージすると5個になる(在庫100なのでクランプ無し)"
        mockMvc.perform(post("/api/cart/merge").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('[{"itemId":"EST-1","quantity":2}]'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items[0].quantity').value(5))
    }

    def "計画②: mergeで合算が在庫数を超える場合は拒否せず在庫数にクランプする(EST-2, stock=1)"() {
        expect:
        mockMvc.perform(post("/api/cart/merge").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('[{"itemId":"EST-2","quantity":3}]'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items[0].quantity').value(1))
    }

    def "ID-28: レスポンスJSONに在庫数そのもの(stockQuantity)フィールドは含まれない"() {
        given:
        def result = mockMvc.perform(post("/api/cart/items").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-2","quantity":1}'))
                .andExpect(status().isOk())
                .andReturn()

        expect:
        def body = result.response.contentAsString.toLowerCase()
        !body.contains("stockquantity")
        !body.contains("stock_quantity")
    }

    def "CSRFトークン無しでPOST /api/cart/itemsすると403になる(未認証でも認証済みでも)"() {
        expect: "未認証+CSRF無し"
        mockMvc.perform(post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-1","quantity":1}'))
                .andExpect(status().isForbidden())

        and: "認証済み+CSRF無し"
        mockMvc.perform(post("/api/cart/items").cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"itemId":"EST-1","quantity":1}'))
                .andExpect(status().isForbidden())
    }

    def "D1: orderable EPは未認証でも到達できる(200・在庫あり)"() {
        expect:
        mockMvc.perform(get("/api/items/EST-1/orderable").param("quantity", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.orderable').value(true))
                .andExpect(jsonPath('$.reason').doesNotExist())
    }

    def "D1: orderable EPは在庫切れならorderable=false, reason=OUT_OF_STOCKを返す(EST-3)"() {
        expect:
        mockMvc.perform(get("/api/items/EST-3/orderable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.orderable').value(false))
                .andExpect(jsonPath('$.reason').value("OUT_OF_STOCK"))
    }

    def "D1: orderable EPは数量が在庫を超えるとorderable=false, reason=EXCEEDS_STOCKを返す(EST-2, stock=1に2要求)"() {
        expect:
        mockMvc.perform(get("/api/items/EST-2/orderable").param("quantity", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.orderable').value(false))
                .andExpect(jsonPath('$.reason').value("EXCEEDS_STOCK"))
    }

    def "D1/AC4: orderable EPは存在しないitemIdで404になる"() {
        expect:
        mockMvc.perform(get("/api/items/NOPE/orderable"))
                .andExpect(status().isNotFound())
    }

    def "D1/ID-28: orderable EPのレスポンスに在庫数そのものは含まれない"() {
        given:
        def result = mockMvc.perform(get("/api/items/EST-2/orderable"))
                .andExpect(status().isOk())
                .andReturn()

        expect:
        def body = result.response.contentAsString.toLowerCase()
        !body.contains("quantity")
        !body.contains("stock")
    }
}
