package com.example.jpetstore.backend.infrastructure.mybatis.account

import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AccountContactCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.AccountContactCustomMapper
import spock.lang.Specification

/**
 * #30: {@link MyBatisAccountRepository} が {@link AccountContactCustomMapper} を1回だけ呼びEntity→Domain変換する
 * ことを純UT（Mapper mock・DB非依存）で検証する。
 */
class MyBatisAccountRepositorySpec extends Specification {

    private static final Long USER_ID = 42L

    AccountContactCustomMapper accountContactCustomMapper = Mock()

    MyBatisAccountRepository repository = new MyBatisAccountRepository(accountContactCustomMapper)

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
}
