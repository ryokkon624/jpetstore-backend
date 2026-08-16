package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper

import com.example.jpetstore.backend.support.IntegrationTestBase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Tag

/**
 * #7: プリフィル用の住所/氏名参照専用カスタムマッパー（{@code m_account} 単一SELECT・read-only）を検証する。
 *
 * <p>E4/F4.2（編集側）・#8（送信/在庫）とは重複させず、参照のみに厳格限定する（計画フェーズ確定①）。
 */
@Tag("integration")
class AccountContactCustomMapperSpec extends IntegrationTestBase {

    private static final String USERNAME = "account_contact_mapper_test_user"

    @Autowired
    AccountContactCustomMapper mapper

    @Autowired
    JdbcTemplate jdbcTemplate

    Long userId

    void setup() {
        jdbcTemplate.update("DELETE FROM m_account WHERE username = ?", USERNAME)
        jdbcTemplate.update(
                """
                INSERT INTO m_account
                    (username, email, first_name, last_name, status, address1, address2, city, state, postal_code, country, phone,
                     create_program, update_program)
                VALUES (?, ?, 'Contact', 'MapperTestUser', 'OK', '1 Test St', 'Suite 2', 'Testville', 'CA', '90000', 'USA', '555-0100',
                     'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                USERNAME, "${USERNAME}@example.com")
        userId = jdbcTemplate.queryForObject("SELECT user_id FROM m_account WHERE username = ?", Long, USERNAME)
    }

    def "findByUserIdは対象ユーザの氏名/連絡先/住所全項目を返す(username/status/version/WHO列は含まない設計)"() {
        expect:
        with(mapper.findByUserId(userId)) {
            firstName == "Contact"
            lastName == "MapperTestUser"
            email == "${USERNAME}@example.com"
            phone == "555-0100"
            address1 == "1 Test St"
            address2 == "Suite 2"
            city == "Testville"
            state == "CA"
            postalCode == "90000"
            country == "USA"
        }
    }

    def "findByUserIdはaddress2がNULLの行もnullのまま返す(任意項目)"() {
        given:
        jdbcTemplate.update("UPDATE m_account SET address2 = NULL WHERE user_id = ?", userId)

        expect:
        mapper.findByUserId(userId).address2 == null
    }

    def "findByUserIdは存在しないuserIdでnullを返す"() {
        expect:
        mapper.findByUserId(-1L) == null
    }
}
