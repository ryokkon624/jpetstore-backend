package com.example.jpetstore.backend.presentation.rest

import com.example.jpetstore.backend.infrastructure.security.AuthCookieSupport
import com.example.jpetstore.backend.support.IntegrationTestBase
import com.example.jpetstore.backend.support.TestJwtSecrets
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Tag

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * #38 Q3: L3 N1 のライブPoC（{@code .env.example} の公開 placeholder 鍵で署名した偽造トークン）を
 * 名指しで固定する回帰テスト。出典: {@code reports/after/l3-security-regression-backend.md} §2.1 N1
 * （SEC・run {@code security/20260819_01}）。
 *
 * <p>本 IT は実アプリを {@link TestJwtSecrets#STRONG}（正当な鍵相当）で起動する。#38 AC1 導入後、
 * {@code .env.example} の実リテラルは denylist により起動そのものを拒否するため、稼働鍵として
 * その値を設定した状態のアプリは作れない（その事実自体は {@code JwtSecretContextFailFastSpec} の
 * AC-neg1 で別途固定済み）。ここでは「稼働鍵が正当である限り、公開済みの {@code .env.example} 値で
 * 署名した偽造トークンは常に拒否される」ことを HTTP 層で実証し、AC-neg1（起動時 denylist）と対で
 * N1 の攻撃経路（① placeholder が実運用可能 → ② それで署名すれば認証を偽造できる）の両端が
 * 閉じることを示す。
 */
@Tag("integration")
@AutoConfigureMockMvc
class JwtForgedTokenL3RegressionSpec extends IntegrationTestBase {

    @Autowired
    MockMvc mockMvc

    /** SEC のライブPoCと同じ構造（typ/username/roles claim付き）のトークンを、公開鍵で直接偽造する。 */
    private static String forgeToken(Long userId, String username, List<String> roles) {
        def key = Keys.hmacShaKeyFor(TestJwtSecrets.ENV_EXAMPLE_PLACEHOLDER.getBytes(StandardCharsets.UTF_8))
        def now = Instant.now()
        Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("roles", roles)
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(15))))
                .signWith(key)
                .compact()
    }

    def "L3 N1: .env.exampleの公開鍵で署名した偽造access tokenはGET /api/auth/meで401になる(資格情報なしのなりすまし阻止)"() {
        given:
        def forged = forgeToken(1L, "ac_neg1_user", ["USER"])

        expect:
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie(AuthCookieSupport.ACCESS_TOKEN_COOKIE, forged)))
                .andExpect(status().isUnauthorized())
    }

    def "L3 N1: .env.exampleの公開鍵で署名したroles=[ADMIN]偽造tokenはADMIN限定エンドポイントで401になる(垂直昇格阻止)"() {
        given:
        def forged = forgeToken(9999L, "attacker", ["ADMIN"])

        expect:
        mockMvc.perform(get("/api/secured/ping").cookie(new Cookie(AuthCookieSupport.ACCESS_TOKEN_COOKIE, forged)))
                .andExpect(status().isUnauthorized())
    }
}
