package com.example.jpetstore.backend.presentation.rest

import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.infrastructure.security.AuthCookieSupport
import com.example.jpetstore.backend.infrastructure.security.JwtService
import com.example.jpetstore.backend.support.IntegrationTestBase
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Tag

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * #21 AC1/AC2/AC-neg1 の end-to-end 実証。
 *
 * <p>{@code /api/secured/my-resource/{resourceOwnerUserId}}（本 Story で唯一追加する実証エンドポイント）を通し、
 * {@code OwnershipAuthorizationService} が {@code CurrentUserProvider}（JWT検証済みprincipal）だけを identity 源として
 * 本人スコープ認可を行うこと、認可失敗が監査ログに記録されること、リクエストの form/param 値（{@code ?userId=...}）が
 * 認可結果に一切影響しないことを検証する。ADMIN ロールは使わず USER プリンシパル2つで所有者/非所有者を表現する。
 */
@Tag("integration")
@AutoConfigureMockMvc
class OwnershipAuthorizationEndToEndSpec extends IntegrationTestBase {

    private static final Long OWNER_USER_ID = 101L
    private static final Long OTHER_USER_ID = 202L

    @Autowired
    MockMvc mockMvc

    @Autowired
    JwtService jwtService

    @Autowired
    JdbcTemplate jdbcTemplate

    void setup() {
        jdbcTemplate.update("DELETE FROM t_audit_log")
    }

    private Cookie accessTokenCookie(Long userId, String username) {
        def user = new AuthenticatedUser(userId, username, ["USER"])
        new Cookie(AuthCookieSupport.ACCESS_TOKEN_COOKIE, jwtService.generateAccessToken(user))
    }

    def "所有者(userIdが一致)がアクセスすると200を返す(AC1)"() {
        expect:
        mockMvc.perform(get("/api/secured/my-resource/${OWNER_USER_ID}")
                .cookie(accessTokenCookie(OWNER_USER_ID, "owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.status').value("ok"))
    }

    def "非所有者(userIdが不一致)がアクセスすると403かつ監査ログに記録される(AC1/AC2)"() {
        when:
        def result = mockMvc.perform(get("/api/secured/my-resource/${OWNER_USER_ID}")
                .cookie(accessTokenCookie(OTHER_USER_ID, "other")))

        then:
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath('$.code').value("FORBIDDEN"))

        and:
        jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_audit_log WHERE event_type='AUTHZ_FAILURE' AND result='DENIED' AND actor_user_id = ?",
                Integer, OTHER_USER_ID) == 1
    }

    def "未認証(Cookie無し)でアクセスすると401かつ監査ログに記録される(AC2)"() {
        when:
        def result = mockMvc.perform(get("/api/secured/my-resource/${OWNER_USER_ID}"))

        then:
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath('$.code').value("UNAUTHORIZED"))

        and:
        jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_audit_log WHERE event_type='AUTHZ_FAILURE' AND result='DENIED'",
                Integer) == 1
    }

    def "?userId=他人を付与しても認可結果は変わらない(AC-neg1・form/param束縛値を認可に使わない)"() {
        expect: "所有者本人は?userId=他人を付けても引き続き200(principal基準)"
        mockMvc.perform(get("/api/secured/my-resource/${OWNER_USER_ID}?userId=${OTHER_USER_ID}")
                .cookie(accessTokenCookie(OWNER_USER_ID, "owner")))
                .andExpect(status().isOk())

        and: "非所有者は?userId=自分を付けても引き続き403(principal基準・paramで昇格しない)"
        mockMvc.perform(get("/api/secured/my-resource/${OWNER_USER_ID}?userId=${OWNER_USER_ID}")
                .cookie(accessTokenCookie(OTHER_USER_ID, "other")))
                .andExpect(status().isForbidden())
    }
}
