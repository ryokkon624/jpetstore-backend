package com.example.jpetstore.backend.parity.verify

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import spock.lang.Tag

/**
 * {@link NewHttpClient} の CSRF ヘルパ + ログイン疎通確認（#48・SM申し送り②）。
 *
 * <p>「トークンが取れるまで GET を繰り返す」ヘルパが実際に機能し、ログイン（CSRF付きPOST）→
 * 認証必須エンドポイント（{@code GET /api/cart}）到達までが実HTTPで通ることを実証する。
 * ここを雑にやると原因の分かりにくい403に延々ハマる（design.md F3・spikeで4回踏んだ落とし穴）。
 */
@Tag("integration")
@Tag("parity")
class NewHttpClientSpec extends ParityIntegrationTestBase {

    private static final String USERNAME = "parity_csrf_smoke_user"
    private static final String RAW_PASSWORD = "Parity#Smoke1!"

    @Autowired
    JdbcTemplate jdbcTemplate

    @Autowired
    PasswordEncoder passwordEncoder

    void setup() {
        cleanup()
        jdbcTemplate.update(
                """
                INSERT INTO m_account
                    (username, email, first_name, last_name, status, address1, city, state, postal_code, country, phone,
                     create_program, update_program)
                VALUES (?, ?, 'Parity', 'CsrfSmokeUser', 'OK', '1 Test St', 'Testville', 'CA', '90000', 'USA', '555-0100',
                     'PARITY_FIXTURE', 'PARITY_FIXTURE')
                """,
                USERNAME, "${USERNAME}@example.com")
        Long userId = jdbcTemplate.queryForObject("SELECT user_id FROM m_account WHERE username = ?", Long, USERNAME)
        jdbcTemplate.update(
                """
                INSERT INTO m_signon (user_id, password_hash, create_program, update_program)
                VALUES (?, ?, 'PARITY_FIXTURE', 'PARITY_FIXTURE')
                """,
                userId, passwordEncoder.encode(RAW_PASSWORD))
    }

    void cleanup() {
        jdbcTemplate.update("DELETE FROM t_cart_item WHERE cart_id IN (SELECT cart_id FROM t_cart WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?))", USERNAME)
        jdbcTemplate.update("DELETE FROM t_cart WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)", USERNAME)
        jdbcTemplate.update("DELETE FROM t_login_attempt WHERE username = ?", USERNAME)
        jdbcTemplate.update("DELETE FROM m_signon WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)", USERNAME)
        jdbcTemplate.update("DELETE FROM m_account WHERE username = ?", USERNAME)
    }

    def "ensureCsrfTokenが非空のXSRF-TOKENを取得できる"() {
        expect:
        !new NewHttpClient(baseUrl()).ensureCsrfToken().isBlank()
    }

    def "ログイン(CSRF付きPOST)→ACCESS_TOKEN発行→認証必須GETへ到達できる"() {
        given:
        def client = new NewHttpClient(baseUrl())

        when:
        def loginResponse = client.login(USERNAME, RAW_PASSWORD)

        then:
        loginResponse.statusCode() == 200
        loginResponse.body().contains(USERNAME)
        client.cookieSnapshot().containsKey("ACCESS_TOKEN")

        when:
        def cartResponse = client.get("/api/cart")

        then:
        cartResponse.statusCode() == 200
    }

    def "CSRFトークン無しの生POSTは403になる(ensureCsrfTokenを経由しない場合の対照実験)"() {
        given:
        def client = new NewHttpClient(baseUrl())
        // ensureCsrfTokenを一切呼ばず、Cookie/ヘッダ無しで直接loginエンドポイントへPOSTする体で
        // NewHttpClient経由ではなくrawなHttpClientで再現する。
        def raw = java.net.http.HttpClient.newHttpClient()
        def request = java.net.http.HttpRequest.newBuilder(URI.create("${baseUrl()}/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                        /{"username":"${USERNAME}","password":"${RAW_PASSWORD}"}/))
                .build()

        expect:
        raw.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode() == 403
    }
}
