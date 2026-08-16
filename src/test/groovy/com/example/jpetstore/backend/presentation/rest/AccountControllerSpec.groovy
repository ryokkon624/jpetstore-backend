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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * #7: プリフィル用の住所/氏名参照API {@code GET /api/account/me} のend-to-end実証（計画フェーズ確定①）。
 *
 * <p>{@code SecurityConfig} は無変更（{@code /api/account/**} は既存の {@code anyRequest().authenticated()}
 * 配下＝常に認証必須。permitAllに追加しない）。read-onlyに厳格限定し、非GETは受理しない（405）。
 */
@Tag("integration")
@AutoConfigureMockMvc
class AccountControllerSpec extends IntegrationTestBase {

    private static final String USERNAME = "account_controller_test_user"

    @Autowired
    MockMvc mockMvc

    @Autowired
    JwtService jwtService

    @Autowired
    JdbcTemplate jdbcTemplate

    Long userId
    Cookie accessTokenCookie

    void setup() {
        jdbcTemplate.update("DELETE FROM m_account WHERE username = ?", USERNAME)
        jdbcTemplate.update(
                """
                INSERT INTO m_account
                    (username, email, first_name, last_name, status, address1, address2, city, state, postal_code, country, phone,
                     create_program, update_program)
                VALUES (?, ?, 'Account', 'ControllerTestUser', 'OK', '1 Test St', 'Suite 2', 'Testville', 'CA', '90000', 'USA', '555-0100',
                     'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                USERNAME, "${USERNAME}@example.com")
        userId = jdbcTemplate.queryForObject("SELECT user_id FROM m_account WHERE username = ?", Long, USERNAME)
        def user = new AuthenticatedUser(userId, USERNAME, ["USER"])
        accessTokenCookie = new Cookie(AuthCookieSupport.ACCESS_TOKEN_COOKIE, jwtService.generateAccessToken(user))
    }

    def "認証済みでGET /api/account/meを叩くと200で氏名/連絡先/住所を返す(username/status/version/WHO列やカード列は含まない)"() {
        given:
        def result = mockMvc.perform(get("/api/account/me").cookie(accessTokenCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.firstName').value("Account"))
                .andExpect(jsonPath('$.lastName').value("ControllerTestUser"))
                .andExpect(jsonPath('$.email').value("${USERNAME}@example.com".toString()))
                .andExpect(jsonPath('$.phone').value("555-0100"))
                .andExpect(jsonPath('$.address1').value("1 Test St"))
                .andExpect(jsonPath('$.address2').value("Suite 2"))
                .andExpect(jsonPath('$.city').value("Testville"))
                .andExpect(jsonPath('$.state').value("CA"))
                .andExpect(jsonPath('$.postalCode').value("90000"))
                .andExpect(jsonPath('$.country').value("USA"))
                .andReturn()

        expect: "username/status/version/WHO列・カード列は一切露出しない"
        def body = result.response.contentAsString.toLowerCase()
        !body.contains("username")
        !body.contains("status")
        !body.contains("version")
        !body.contains("password")
        !body.contains("create_")
        !body.contains("update_")
    }

    def "未認証(Cookie無し)でGET /api/account/meを叩くと401になる"() {
        expect:
        mockMvc.perform(get("/api/account/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath('$.code').value("UNAUTHORIZED"))
    }

    def "read-onlyに厳格限定: POST /api/account/meは405になる(送信/編集APIは存在しない)"() {
        expect:
        mockMvc.perform(post("/api/account/me").with(csrf()).cookie(accessTokenCookie))
                .andExpect(status().isMethodNotAllowed())
    }
}
