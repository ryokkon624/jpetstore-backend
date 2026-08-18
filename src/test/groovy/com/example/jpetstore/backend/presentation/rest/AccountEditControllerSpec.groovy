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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * #14 AC1〜AC3・AC-neg1〜3: アカウント/プロフィール編集（本人固定・allowlist・version楽観ロック）のend-to-end実証。
 *
 * <p>{@code GET /api/account/me}（#7・無変更）とは別の新規エンドポイント（E3）。{@code SecurityConfig}は
 * 無変更（{@code /api/account/**}は既存の{@code anyRequest().authenticated()}配下）。
 */
@Tag("integration")
@AutoConfigureMockMvc
class AccountEditControllerSpec extends IntegrationTestBase {

    private static final String USERNAME = "account_edit_test_user"
    private static final String OTHER_USERNAME = "account_edit_test_other_user"

    @Autowired
    MockMvc mockMvc

    @Autowired
    JwtService jwtService

    @Autowired
    JdbcTemplate jdbcTemplate

    Long userId
    Long otherUserId
    Cookie accessTokenCookie
    Cookie otherAccessTokenCookie

    void setup() {
        cleanupFixtures()
        userId = insertTestUser(USERNAME)
        otherUserId = insertTestUser(OTHER_USERNAME)
        accessTokenCookie = new Cookie(
                AuthCookieSupport.ACCESS_TOKEN_COOKIE,
                jwtService.generateAccessToken(new AuthenticatedUser(userId, USERNAME, ["USER"])))
        otherAccessTokenCookie = new Cookie(
                AuthCookieSupport.ACCESS_TOKEN_COOKIE,
                jwtService.generateAccessToken(new AuthenticatedUser(otherUserId, OTHER_USERNAME, ["USER"])))
    }

    void cleanup() {
        cleanupFixtures()
    }

    private void cleanupFixtures() {
        jdbcTemplate.update(
                "DELETE FROM m_profile WHERE user_id IN (SELECT user_id FROM m_account WHERE username IN (?, ?))",
                USERNAME, OTHER_USERNAME)
        jdbcTemplate.update("DELETE FROM m_account WHERE username IN (?, ?)", USERNAME, OTHER_USERNAME)
    }

    private Long insertTestUser(String username) {
        jdbcTemplate.update(
                """
                INSERT INTO m_account
                    (username, email, first_name, last_name, status, address1, address2, city, state, postal_code, country, phone,
                     create_program, update_program)
                VALUES (?, ?, 'Account', 'EditTestUser', 'OK', '1 Test St', 'Suite 2', 'Testville', 'CA', '90000', 'USA', '555-0100',
                     'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                username, "${username}@example.com")
        Long id = jdbcTemplate.queryForObject("SELECT user_id FROM m_account WHERE username = ?", Long, username)
        jdbcTemplate.update(
                """
                INSERT INTO m_profile (user_id, language_preference, favorite_category_id, create_program, update_program)
                VALUES (?, 'english', 'FISH', 'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                id)
        id
    }

    private static String updateBody(long version, String firstName = "Jiro") {
        """
        {
          "version": ${version}, "firstName": "${firstName}", "lastName": "Suzuki",
          "email": "jiro@example.com", "phone": "555-0199", "address1": "2 New St", "address2": null,
          "city": "Newtown", "state": "NY", "postalCode": "10001", "country": "USA",
          "languagePreference": "japanese", "favoriteCategoryId": "DOGS"
        }
        """
    }

    def "AC3/E3: GET /api/accountは200でversion込みの編集可フィールドを返す"() {
        expect:
        mockMvc.perform(get("/api/account").cookie(accessTokenCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.firstName').value("Account"))
                .andExpect(jsonPath('$.lastName').value("EditTestUser"))
                .andExpect(jsonPath('$.languagePreference').value("english"))
                .andExpect(jsonPath('$.favoriteCategoryId').value("FISH"))
                .andExpect(jsonPath('$.version').value(0))
    }

    def "未認証(Cookie無し)でGET /api/accountを叩くと401になる"() {
        expect:
        mockMvc.perform(get("/api/account"))
                .andExpect(status().isUnauthorized())
    }

    def "AC1〜AC3: 正しいversionでPUT /api/accountすると200で更新され新しいversionを返す"() {
        expect:
        mockMvc.perform(put("/api/account").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.firstName').value("Jiro"))
                .andExpect(jsonPath('$.email').value("jiro@example.com"))
                .andExpect(jsonPath('$.languagePreference').value("japanese"))
                .andExpect(jsonPath('$.favoriteCategoryId').value("DOGS"))
                .andExpect(jsonPath('$.version').value(1))

        and: "DBにも反映される(m_account/m_profileとも)"
        jdbcTemplate.queryForObject("SELECT first_name FROM m_account WHERE user_id = ?", String, userId) == "Jiro"
        jdbcTemplate.queryForObject("SELECT version FROM m_account WHERE user_id = ?", Long, userId) == 1L
        jdbcTemplate.queryForObject(
                "SELECT language_preference FROM m_profile WHERE user_id = ?", String, userId) == "japanese"
    }

    def "AC-neg1(SBD-1): 他人のuserIdを注入してもリクエストは常に自分自身のみを更新する"() {
        given: "otherユーザーの現在の氏名を確認しておく"
        def otherFirstNameBefore = jdbcTemplate.queryForObject(
                "SELECT first_name FROM m_account WHERE user_id = ?", String, otherUserId)

        when: "自分(userId)のCookieでPUT /api/accountを叩く(usernameフィールド自体がDTOに存在しないため注入不能)"
        mockMvc.perform(put("/api/account").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(0)))
                .andExpect(status().isOk())

        then: "自分の行のみ更新され、他人の行は変更されない"
        jdbcTemplate.queryForObject("SELECT first_name FROM m_account WHERE user_id = ?", String, userId) == "Jiro"
        jdbcTemplate.queryForObject("SELECT first_name FROM m_account WHERE user_id = ?", String, otherUserId) == otherFirstNameBefore
    }

    def "AC-neg2(SBD-2): 権威フィールド(userId/status/version上書き)を注入しても無視される"() {
        given:
        def body = '''
        {
          "version": 0, "firstName": "Jiro", "lastName": "Suzuki", "email": "jiro@example.com",
          "phone": "555-0199", "address1": "2 New St", "address2": null, "city": "Newtown",
          "state": "NY", "postalCode": "10001", "country": "USA",
          "languagePreference": "japanese", "favoriteCategoryId": "DOGS",
          "userId": 999999, "username": "hijacked", "status": "CLOSED"
        }
        '''

        when:
        mockMvc.perform(put("/api/account").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())

        then: "本人(userId)のusername/statusは変更されない(そもそもUPDATE文にusername/status列を含まない)"
        jdbcTemplate.queryForObject("SELECT username FROM m_account WHERE user_id = ?", String, userId) == USERNAME
        jdbcTemplate.queryForObject("SELECT status FROM m_account WHERE user_id = ?", String, userId) == "OK"

        and: "注入したuserId(999999)には何も起きない(他人/不存在行は無傷)"
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM m_account WHERE user_id = 999999", Integer) == 0
    }

    def "AC-neg3(arch §4.2): 古いversionでPUT /api/accountすると409になり最新の再読込を促す(lost updateなし)"() {
        given: "先にversion0→1へ更新しておく"
        mockMvc.perform(put("/api/account").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(0)))
                .andExpect(status().isOk())

        expect: "古いversion(0)のままの2回目の更新は409"
        mockMvc.perform(put("/api/account").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(0, "Saburo")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath('$.code').value("CONFLICT"))

        and: "409側の変更(Saburo)は反映されず、1回目の更新結果(Jiro)のまま(lost updateなし)"
        jdbcTemplate.queryForObject("SELECT first_name FROM m_account WHERE user_id = ?", String, userId) == "Jiro"
        jdbcTemplate.queryForObject("SELECT version FROM m_account WHERE user_id = ?", Long, userId) == 1L
    }

    def "AC-neg3(arch §4.2): 同一readVersionへの2並行PUTは1件200/1件409になり、最終行は勝者のみでlost updateが起きない"() {
        given: "2スレッドが同時にPUT /api/accountを叩けるようCountDownLatchで同期する(#8の2段ラッチ手法を応用)"
        ExecutorService executor = Executors.newFixedThreadPool(2)
        CountDownLatch readyLatch = new CountDownLatch(2)
        CountDownLatch startLatch = new CountDownLatch(1)

        when:
        def futureA = executor.submit({
            readyLatch.countDown()
            startLatch.await()
            mockMvc.perform(put("/api/account").with(csrf()).cookie(accessTokenCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateBody(0, "Winner-A"))).andReturn().response.status
        } as java.util.concurrent.Callable<Integer>)
        def futureB = executor.submit({
            readyLatch.countDown()
            startLatch.await()
            mockMvc.perform(put("/api/account").with(csrf()).cookie(accessTokenCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateBody(0, "Winner-B"))).andReturn().response.status
        } as java.util.concurrent.Callable<Integer>)

        readyLatch.await(5, TimeUnit.SECONDS)
        startLatch.countDown()
        int statusA = futureA.get(15, TimeUnit.SECONDS)
        int statusB = futureB.get(15, TimeUnit.SECONDS)
        executor.shutdown()

        then: "片方のみ200(成功)・もう片方は409(競合)"
        def statuses = [statusA, statusB]
        statuses.count { it == 200 } == 1
        statuses.count { it == 409 } == 1

        and: "最終行のfirst_nameは勝者(Winner-A/Winner-Bのいずれか)のみ・versionは1のまま(lost updateなし)"
        def finalName = jdbcTemplate.queryForObject("SELECT first_name FROM m_account WHERE user_id = ?", String, userId)
        finalName in ["Winner-A", "Winner-B"]
        jdbcTemplate.queryForObject("SELECT version FROM m_account WHERE user_id = ?", Long, userId) == 1L
    }

    def "GET/PUT /api/accountはCSRFトークン無しのPUTだと403になる(SBD-3)"() {
        expect:
        mockMvc.perform(put("/api/account").cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(0)))
                .andExpect(status().isForbidden())
    }

    def "AC-neg1(#17 Q2): 不正なemail形式は400になり、更新されない"() {
        given:
        def body = updateBody(0).toString().replace('"email": "jiro@example.com"', '"email": "not-an-email"')

        expect:
        mockMvc.perform(put("/api/account").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())

        and:
        jdbcTemplate.queryForObject("SELECT first_name FROM m_account WHERE user_id = ?", String, userId) == "Account"
    }

    def "AC-neg1(#17 Q2): firstNameがDBカラム幅(80)を超えると400になる"() {
        given:
        def body = updateBody(0, "x" * 81)

        expect:
        mockMvc.perform(put("/api/account").with(csrf()).cookie(accessTokenCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
    }
}
