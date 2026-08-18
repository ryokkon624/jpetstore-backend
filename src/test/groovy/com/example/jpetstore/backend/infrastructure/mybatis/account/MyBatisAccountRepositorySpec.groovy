package com.example.jpetstore.backend.infrastructure.mybatis.account

import com.example.jpetstore.backend.domain.account.AccountUpdate
import com.example.jpetstore.backend.domain.account.NewAccountRegistration
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AccountContactCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AccountEditCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AccountPreferencesCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AccountRegistrationCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AccountUpdateCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ProfileRegistrationCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ProfileUpdateCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.SignonRegistrationCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.SignonUpdateCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.AccountContactCustomMapper
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.AccountEditCustomMapper
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.AccountPreferencesCustomMapper
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.AccountRegistrationCustomMapper
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.SignonCustomMapper
import spock.lang.Specification

/**
 * #30: {@link MyBatisAccountRepository} が {@link AccountContactCustomMapper} を1回だけ呼びEntity→Domain変換する
 * ことを純UT（Mapper mock・DB非依存）で検証する。
 *
 * <p>#13: {@code register} はm_account/m_signon/m_profileへ順にINSERTし、生成されたuserIdを返すことを検証する。
 * create_user_id/update_user_idは常にNULL（未認証guestによる登録・E7）で明示設定される。
 *
 * <p>#14: {@code findEditDetailByUserId}/{@code updateAccount}（version楽観ロック）を検証する。
 */
class MyBatisAccountRepositorySpec extends Specification {

    private static final Long USER_ID = 42L

    AccountContactCustomMapper accountContactCustomMapper = Mock()
    AccountRegistrationCustomMapper accountRegistrationCustomMapper = Mock()
    AccountEditCustomMapper accountEditCustomMapper = Mock()
    AccountPreferencesCustomMapper accountPreferencesCustomMapper = Mock()
    SignonCustomMapper signonCustomMapper = Mock()

    MyBatisAccountRepository repository = new MyBatisAccountRepository(
            accountContactCustomMapper, accountRegistrationCustomMapper, accountEditCustomMapper,
            accountPreferencesCustomMapper, signonCustomMapper)

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

    private static AccountPreferencesCustomEntity preferencesEntity() {
        def e = new AccountPreferencesCustomEntity()
        e.colorSchemePreference = "dark"
        e.languagePreference = "japanese"
        e
    }

    def "findPreferencesByUserId: mapperを1回呼びEntity→UserPreferencesへ変換して返す(#36/#25)"() {
        when:
        def result = repository.findPreferencesByUserId(USER_ID)

        then:
        1 * accountPreferencesCustomMapper.findByUserId(USER_ID) >> preferencesEntity()
        result.isPresent()
        result.get().colorSchemePreference() == "dark"
        result.get().languagePreference() == "japanese"
    }

    def "findPreferencesByUserId: mapperがnullを返した場合はOptional.emptyを返す"() {
        when:
        def result = repository.findPreferencesByUserId(USER_ID)

        then:
        1 * accountPreferencesCustomMapper.findByUserId(USER_ID) >> null
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

    private static AccountEditCustomEntity editEntity() {
        def e = new AccountEditCustomEntity()
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
        e.languagePreference = "english"
        e.favoriteCategoryId = "FISH"
        e.colorSchemePreference = "dark"
        e.version = 3L
        e
    }

    def "findEditDetailByUserId: mapperを1回呼びEntity→AccountEditDetailへ変換して返す(version込み)"() {
        when:
        def result = repository.findEditDetailByUserId(USER_ID)

        then:
        1 * accountEditCustomMapper.findByUserId(USER_ID) >> editEntity()
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
        result.get().languagePreference() == "english"
        result.get().favoriteCategoryId() == "FISH"
        result.get().colorSchemePreference() == "dark"
        result.get().version() == 3L
    }

    def "findEditDetailByUserId: mapperがnullを返した場合はOptional.emptyを返す"() {
        when:
        def result = repository.findEditDetailByUserId(USER_ID)

        then:
        1 * accountEditCustomMapper.findByUserId(USER_ID) >> null
        result.isEmpty()
    }

    private static AccountUpdate accountUpdate() {
        new AccountUpdate(
                USER_ID, 3L, "Taro", "Yamada", "taro2@example.com", "555-0199",
                "2 New St", null, "Newtown", "NY", "10001", "USA",
                "japanese", "DOGS", "light")
    }

    def "updateAccount: version楽観ロック付きでm_accountを更新し、成功すればm_profileも無ガードで更新する"() {
        given:
        def update = accountUpdate()

        when:
        def affected = repository.updateAccount(update)

        then:
        1 * accountEditCustomMapper.updateAccount({ AccountUpdateCustomEntity e ->
            e.userId == USER_ID &&
                    e.expectedVersion == 3L &&
                    e.firstName == "Taro" &&
                    e.lastName == "Yamada" &&
                    e.email == "taro2@example.com" &&
                    e.phone == "555-0199" &&
                    e.address1 == "2 New St" &&
                    e.address2 == null &&
                    e.city == "Newtown" &&
                    e.state == "NY" &&
                    e.postalCode == "10001" &&
                    e.country == "USA" &&
                    e.updateUserId == USER_ID
        }) >> 1
        1 * accountEditCustomMapper.updateProfile({ ProfileUpdateCustomEntity e ->
            e.userId == USER_ID &&
                    e.languagePreference == "japanese" &&
                    e.favoriteCategoryId == "DOGS" &&
                    e.colorSchemePreference == "light" &&
                    e.updateUserId == USER_ID
        })
        affected == 1
    }

    def "updateAccount: m_accountのUPDATEが競合(affected=0)した場合、m_profileは更新しない"() {
        given:
        def update = accountUpdate()

        when:
        def affected = repository.updateAccount(update)

        then:
        1 * accountEditCustomMapper.updateAccount(_) >> 0
        0 * accountEditCustomMapper.updateProfile(_)
        affected == 0
    }

    def "findPasswordHashByUserId: mapperを1回呼びOptionalへラップして返す(#15)"() {
        when:
        def result = repository.findPasswordHashByUserId(USER_ID)

        then:
        1 * signonCustomMapper.findPasswordHashByUserId(USER_ID) >> "{bcrypt}stored-hash"
        result.isPresent()
        result.get() == "{bcrypt}stored-hash"
    }

    def "findPasswordHashByUserId: mapperがnullを返した場合はOptional.emptyを返す"() {
        when:
        def result = repository.findPasswordHashByUserId(USER_ID)

        then:
        1 * signonCustomMapper.findPasswordHashByUserId(USER_ID) >> null
        result.isEmpty()
    }

    def "updatePassword: userId/passwordHash/updateUserIdをentityへ詰めてmapperを1回呼ぶ(m_signonはversion楽観ロック対象外)(#15)"() {
        when:
        def affected = repository.updatePassword(USER_ID, "{bcrypt}new-hash")

        then:
        1 * signonCustomMapper.updatePassword({ SignonUpdateCustomEntity e ->
            e.userId == USER_ID && e.passwordHash == "{bcrypt}new-hash" && e.updateUserId == USER_ID
        }) >> 1
        affected == 1
    }
}
