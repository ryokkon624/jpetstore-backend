package com.example.jpetstore.backend.parity

import com.example.jpetstore.backend.parity.canonical.ParityComparator
import com.example.jpetstore.backend.parity.canonical.ParityGolden
import com.example.jpetstore.backend.parity.canonical.ParityGoldenIO
import com.example.jpetstore.backend.parity.canonical.ParitySnapshot
import com.example.jpetstore.backend.parity.verify.NewDbReader
import com.example.jpetstore.backend.parity.verify.NewHttpClient
import com.example.jpetstore.backend.parity.verify.NewScenarioRunner
import com.example.jpetstore.backend.parity.verify.ParityIntegrationTestBase
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Tag
import spock.lang.Unroll

/**
 * アカウント系（W4/W5a/W5b/W5c）の新側verify（#51 AC1/AC2・dual-tag）。
 *
 * <p>各シナリオは自己完結（Q3）: {@code POST /api/register}で自ら新しいユーザーを登録・自動ログインし、
 * 編集・削除まで{@code NewScenarioRunner}の各メソッド内で完結する。{@code OrderParitySpec}のような
 * 事前フィクスチャ（{@code demo_user}）は不要（{@code userId}に0Lを渡す設計は{@code CatalogParitySpec}と同じ
 * 「未使用のためダミー値でよい」パターン）。
 *
 * <p>AC-neg4（新側でのログイン成功／ハッシュ非一致・W5bの新PW成功/旧PW401）はcanonical比較の対象外の
 * 独立検証として別feature methodに置く（goldenには一切載せない）。
 */
@Tag("integration")
@Tag("parity")
class AccountParitySpec extends ParityIntegrationTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper()

    @Autowired
    JdbcTemplate jdbcTemplate

    /**
     * {@code t_register_attempt}を全行DELETEする（Q3付随の必須対応）。本spec1本で複数回{@code POST /api/register}
     * を呼ぶ（W4+W5a/b/cで同一IPから4回・上限5回/15分に肉薄する）ため、setup()で毎回リセットしないと
     * イテレーションを跨いだ残存カウントで429が起きうる（{@code NewScenarioRunner#assertRegisterSucceeded}が
     * 429を検知した場合は原因の分かるメッセージでfailする）。
     */
    void setup() {
        jdbcTemplate.update("DELETE FROM t_register_attempt")
    }

    @Unroll
    def "#scenarioId: 新側がcommit済みgoldenと宣言どおりの結果になる(#51 AC1/AC2/AC6)"() {
        given:
        ParityGolden golden = ParityGoldenIO.readFromClasspath(scenarioId)
        NewHttpClient http = new NewHttpClient(baseUrl())
        NewScenarioRunner runner = new NewScenarioRunner(http, new NewDbReader(jdbcTemplate), 0L)

        when:
        ParitySnapshot actual = runner.run(scenarioId)
        def result = ParityComparator.compare(
                golden.scenario, golden.expectation, golden.divergentFields, golden.snapshot, actual)

        then:
        result.pass
        // 失敗時はresult.messageにフィールド単位の差分(field=... golden(legacy)=... actual(new)=...)が出る(AC-neg1)

        where:
        scenarioId << ["account-register", "account-edit-nopw", "account-edit-pw", "account-edit-pwfield-absent"]
    }

    // ------------------------------------------------------------------
    // AC-neg4: 新側でのPW独立検証（golden比較の外側。canonicalからpasswordを除外した代わりの担保・確定2）
    // ------------------------------------------------------------------

    private static final String USERNAME = "parity_negtest4_w4"
    private static final String PASSWORD = "Parity-Neg4Pw!1"

    def "AC-neg4(W4): 登録したPWでログイン成功し、m_signon.password_hashは平文と一致しない(SBD-5/ID-2案A)"() {
        given:
        NewHttpClient http = new NewHttpClient(baseUrl())
        String registerJson = /{"username":"${USERNAME}","password":"${PASSWORD}","repeatedPassword":"${PASSWORD}",
            "email":"parity.neg4@example.com","firstName":"Neg4","lastName":"W4",
            "address1":"1 Neg4 Way","address2":"","city":"Palo Alto","state":"CA",
            "postalCode":"94303","country":"USA","phone":"555-0199",
            "languagePreference":"english","favoriteCategoryId":"FISH"}/.stripIndent()

        when:
        def registerResponse = http.postJson("/api/register", registerJson)

        then:
        registerResponse.statusCode() == 201

        when: "登録直後は自動ログイン済みなので、資格情報を持たない新しいクライアントで改めてログインする"
        NewHttpClient loginOnlyClient = new NewHttpClient(baseUrl())
        def loginResponse = loginOnlyClient.login(USERNAME, PASSWORD)

        then:
        loginResponse.statusCode() == 200

        and: "格納されたハッシュは平文PWと一致しない"
        Long userId = jdbcTemplate.queryForObject("SELECT user_id FROM m_account WHERE username = ?", Long, USERNAME)
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM m_signon WHERE user_id = ?", String, userId)
        storedHash != PASSWORD

        cleanup:
        cleanupNegTestUser(USERNAME)
    }

    private static final String W5B_NEG_USERNAME = "parity_negtest4_w5b"
    private static final String W5B_NEG_INITIAL_PASSWORD = "Parity-Neg4W5bPw!1"
    private static final String W5B_NEG_NEW_PASSWORD = "Parity-Neg4W5bNewPw!2"

    def "AC-neg4(W5b): PW変更後は新PWでログイン成功・旧PWでログイン失敗(401)"() {
        given: "W5bと同じ手順(登録->PUT->PW変更)で対象アカウントを用意する"
        NewHttpClient http = new NewHttpClient(baseUrl())
        String registerJson = /{"username":"${W5B_NEG_USERNAME}","password":"${W5B_NEG_INITIAL_PASSWORD}",
            "repeatedPassword":"${W5B_NEG_INITIAL_PASSWORD}","email":"parity.neg4w5b@example.com",
            "firstName":"Neg4","lastName":"W5b","address1":"1 Neg4 Way","address2":"","city":"Palo Alto",
            "state":"CA","postalCode":"94303","country":"USA","phone":"555-0198",
            "languagePreference":"english","favoriteCategoryId":"FISH"}/.stripIndent()
        http.postJson("/api/register", registerJson)
        JsonNode accountJson = MAPPER.readTree(http.get("/api/account").body())
        String putJson = MAPPER.writeValueAsString([
                version              : accountJson.get("version").asLong(),
                firstName            : "Neg4", lastName: "W5b-Edited", email: "parity.neg4w5b.edited@example.com",
                phone                : "555-0197", address1: "2 Neg4 Way", address2: "", city: "Palo Alto",
                state                : "CA", postalCode: "94304", country: "USA",
                languagePreference   : "english", favoriteCategoryId: "DOGS", colorSchemePreference: "system",
        ])
        http.putJson("/api/account", putJson)
        String passwordJson = MAPPER.writeValueAsString(
                [currentPassword: W5B_NEG_INITIAL_PASSWORD, newPassword: W5B_NEG_NEW_PASSWORD])

        when:
        def changeResponse = http.postJson("/api/account/password", passwordJson)

        then:
        changeResponse.statusCode() == 204

        when: "新PWでログイン成功"
        def newPasswordLogin = new NewHttpClient(baseUrl()).login(W5B_NEG_USERNAME, W5B_NEG_NEW_PASSWORD)

        then:
        newPasswordLogin.statusCode() == 200

        when: "旧PWでログイン失敗(401)"
        def oldPasswordLogin = new NewHttpClient(baseUrl()).login(W5B_NEG_USERNAME, W5B_NEG_INITIAL_PASSWORD)

        then:
        oldPasswordLogin.statusCode() == 401

        cleanup:
        cleanupNegTestUser(W5B_NEG_USERNAME)
    }

    private void cleanupNegTestUser(String username) {
        jdbcTemplate.update(
                "DELETE FROM t_audit_log WHERE actor_user_id IN (SELECT user_id FROM m_account WHERE username = ?)",
                username)
        jdbcTemplate.update(
                "DELETE FROM m_profile WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)", username)
        jdbcTemplate.update(
                "DELETE FROM m_signon WHERE user_id IN (SELECT user_id FROM m_account WHERE username = ?)", username)
        jdbcTemplate.update("DELETE FROM t_login_attempt WHERE username = ?", username)
        jdbcTemplate.update("DELETE FROM m_account WHERE username = ?", username)
    }
}
