package com.example.jpetstore.backend.presentation.rest

import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.AuditLogCustomMapper
import com.example.jpetstore.backend.infrastructure.security.AuthCookieSupport
import com.example.jpetstore.backend.infrastructure.security.JwtService
import com.example.jpetstore.backend.support.IntegrationTestBase
import jakarta.servlet.http.Cookie
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Tag

import static org.mockito.ArgumentMatchers.any
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * #39 Q3/AC-neg1/AC-neg2: L3 N2（過大長URIでの監査抑止＝403→401化）のライブPoCを名指しで固定する回帰テスト。
 * 出典: {@code reports/after/l3-security-regression-backend.md} §2.1 N2（SEC・run {@code security/20260819_01}）。
 *
 * <p>SEC実測: {@code GET /api/orders/909090909}（21字）→403・監査行 actor=demo_user, action=..., DENIED（正常）。
 * {@code GET /api/orders/<81×'0'>909090909}（102字, 意味的に同一の orderId）→ 修正前は401・監査行は
 * actor=NULL, action=/error（誰が/何をが両方消失）。{@code orderId} は {@code Long} のため先頭ゼロ埋めで
 * URI長を自由に伸ばせる（{@code Long.parseLong} は先頭ゼロの個数に関わらず数値としての大きさのみで
 * オーバーフロー判定するため、桁数をいくら伸ばしても値は変わらない）。
 */
@Tag("integration")
@AutoConfigureMockMvc
class AuditSuppressionL3RegressionSpec extends IntegrationTestBase {

    @Autowired
    MockMvc mockMvc

    @Autowired
    JwtService jwtService

    @Autowired
    JdbcTemplate jdbcTemplate

    @MockitoSpyBean
    AuditLogCustomMapper auditLogCustomMapper

    void setup() {
        jdbcTemplate.update("DELETE FROM t_audit_log")
        jdbcTemplate.update("DELETE FROM t_audit_write_quota")
    }

    /** MockitoSpyBeanは同一Springコンテキストをテストメソッド間で共有するため、stubbingを毎回クリアする。 */
    void cleanup() {
        Mockito.reset(auditLogCustomMapper)
    }

    private Cookie accessTokenCookie(Long userId, String username) {
        def user = new AuthenticatedUser(userId, username, ["USER"])
        new Cookie(AuthCookieSupport.ACCESS_TOKEN_COOKIE, jwtService.generateAccessToken(user))
    }

    def "L3 N2 AC-neg1: 200文字級URIでの認可失敗は403のまま・actorとaction(先頭100文字)を含む監査行が残る(401化・action=/errorへの退行を阻止)"() {
        given: "SECのライブPoCと同一のorderId(先頭ゼロ81個+909090909)で102文字前後のURIにする"
        def longOrderId = "0" * 81 + "909090909"
        def path = "/api/orders/" + longOrderId

        when:
        def result = mockMvc.perform(get(path).cookie(accessTokenCookie(1L, "ac_neg1_user")))

        then: "L3のライブPoCでは401+監査消失に化けたが、修正後は本来の403のまま"
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath('$.code').value("FORBIDDEN"))

        and: "actorとaction(先頭100文字)を含むAUTHZ_FAILURE行が残る(actor=NULL/action=/errorへの退行が無い)"
        def row = jdbcTemplate.queryForMap(
                "SELECT * FROM t_audit_log WHERE event_type='AUTHZ_FAILURE' ORDER BY audit_id DESC LIMIT 1")
        row.actor_username == "ac_neg1_user"
        row.action == path.substring(0, 100)
        row.action != "/error"
        row.result == "DENIED"
    }

    def "L3 N2 AC-neg2: 監査INSERTが失敗しても認可失敗の応答は本来のステータス/ErrorResponseで返る(best-effort e2e)"() {
        given: "INSERT失敗を注入する(cleanup()でstubbingをリセットするため他テストへ波及しない)"
        Mockito.doThrow(new RuntimeException("boom")).when(auditLogCustomMapper).insert(any())

        expect:
        mockMvc.perform(get("/api/orders/909090909").cookie(accessTokenCookie(1L, "ac_neg2_user")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath('$.code').value("FORBIDDEN"))
                .andExpect(jsonPath('$.path').exists())
                .andExpect(jsonPath('$.timestamp').exists())
    }
}
