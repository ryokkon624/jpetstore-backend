package com.example.jpetstore.backend.infrastructure.mybatis.account

import com.example.jpetstore.backend.domain.account.NewAccountRegistration
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AccountContactCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AccountRegistrationCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ProfileRegistrationCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.SignonRegistrationCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.AccountContactCustomMapper
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.AccountRegistrationCustomMapper
import spock.lang.Specification

/**
 * #30: {@link MyBatisAccountRepository} が {@link AccountContactCustomMapper} を1回だけ呼びEntity→Domain変換する
 * ことを純UT（Mapper mock・DB非依存）で検証する。
 *
 * <p>#13: {@code register} はm_account/m_signon/m_profileへ順にINSERTし、生成されたuserIdを返すことを検証する。
 * create_user_id/update_user_idは常にNULL（未認証guestによる登録・E7）で明示設定される。
 */
class MyBatisAccountRepositorySpec extends Specification {

    private static final Long USER_ID = 42L

    AccountContactCustomMapper accountContactCustomMapper = Mock()
    AccountRegistrationCustomMapper accountRegistrationCustomMapper = Mock()

    MyBatisAccountRepository repository =
            new MyBatisAccountRepository(accountContactCustomMapper, accountRegistrationCustomMapper)

    private static AccountContactCustomEntity contactEntity() {
        def e = new AccountContactCustomEntity()
        e.firstName = "Taro"
        e.lastName = "Yamada"
        e.email = "taro@example.com"
        e.phone = "555-0100"
        e.address1 = "1 Test St"
        e.address2 = "Suite 2"
        e.city = "Testville"
        e.state = "CA"
        e.postalCode = "90000"
        e.country = "USA"
        e
    }

    def "findContactByUserId: mapperを1回呼びEntity→AccountContactへ変換して返す"() {
        when:
        def result = repository.findContactByUserId(USER_ID)

        then:
        1 * accountContactCustomMapper.findByUserId(USER_ID) >> contactEntity()
        result.isPresent()
        result.get().firstName() == "Taro"
        result.get().lastName() == "Yamada"
        result.get().email() == "taro@example.com"
        result.get().phone() == "555-0100"
        result.get().address1() == "1 Test St"
        result.get().address2() == "Suite 2"
        result.get().city() == "Testville"
        result.get().state() == "CA"
        result.get().postalCode() == "90000"
        result.get().country() == "USA"
    }

    def "findContactByUserId: mapperがnullを返した場合はOptional.emptyを返す"() {
        when:
        def result = repository.findContactByUserId(USER_ID)

        then:
        1 * accountContactCustomMapper.findByUserId(USER_ID) >> null
        result.isEmpty()
    }

    private static NewAccountRegistration registration() {
        new NewAccountRegistration(
                "new_user", "{bcrypt}hashed", "new_user@example.com", "Taro", "Yamada",
                "1 Test St", "Suite 2", "Testville", "CA", "90000", "USA", "555-0100",
                "english", "FISH")
    }

    def "register: m_account→m_signon→m_profileの順にINSERTし生成されたuserIdを返す"() {
        given:
        def registered = registration()

        when:
        def result = repository.register(registered)

        then:
        1 * accountRegistrationCustomMapper.insertAccount({ AccountRegistrationCustomEntity e ->
            e.username == "new_user" &&
                    e.email == "new_user@example.com" &&
                    e.firstName == "Taro" &&
                    e.lastName == "Yamada" &&
                    e.status == "OK" &&
                    e.address1 == "1 Test St" &&
                    e.address2 == "Suite 2" &&
                    e.city == "Testville" &&
                    e.state == "CA" &&
                    e.postalCode == "90000" &&
                    e.country == "USA" &&
                    e.phone == "555-0100" &&
                    e.createUserId == null &&
                    e.updateUserId == null
        }) >> { AccountRegistrationCustomEntity e -> e.userId = USER_ID }
        1 * accountRegistrationCustomMapper.insertSignon({ SignonRegistrationCustomEntity e ->
            e.userId == USER_ID && e.passwordHash == "{bcrypt}hashed" &&
                    e.createUserId == null && e.updateUserId == null
        })
        1 * accountRegistrationCustomMapper.insertProfile({ ProfileRegistrationCustomEntity e ->
            e.userId == USER_ID && e.languagePreference == "english" && e.favoriteCategoryId == "FISH" &&
                    e.createUserId == null && e.updateUserId == null
        })
        result == USER_ID
    }
}
