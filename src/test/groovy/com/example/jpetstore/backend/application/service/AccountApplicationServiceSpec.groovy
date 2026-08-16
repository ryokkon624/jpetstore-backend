package com.example.jpetstore.backend.application.service

import com.example.jpetstore.backend.domain.exception.ResourceNotFoundException
import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.domain.security.CurrentUserProvider
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AccountContactCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.AccountContactCustomMapper
import org.springframework.security.access.AccessDeniedException
import spock.lang.Specification

/**
 * #7: プリフィル用の自分自身の氏名/連絡先/住所を返すユースケース（read-only・計画フェーズ確定①）を検証する。
 *
 * <p>{@code CurrentUserProvider.requireCurrentUser()} でprincipal→userIdを解決する（クライアント入力のuserIdは
 * 一切受け取らない＝IDOR面ゼロ）。
 */
class AccountApplicationServiceSpec extends Specification {

    private static final Long USER_ID = 42L

    AccountContactCustomMapper accountContactCustomMapper = Mock()
    CurrentUserProvider currentUserProvider = Stub() {
        requireCurrentUser() >> new AuthenticatedUser(USER_ID, "account_user", ["USER"])
    }

    AccountApplicationService service = new AccountApplicationService(accountContactCustomMapper, currentUserProvider)

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

    def "getMyContact: CurrentUserProviderのuserIdでmapperを呼びAccountContactへ変換する"() {
        given:
        accountContactCustomMapper.findByUserId(USER_ID) >> contactEntity()

        when:
        def contact = service.getMyContact()

        then:
        contact.firstName() == "Taro"
        contact.lastName() == "Yamada"
        contact.email() == "taro@example.com"
        contact.phone() == "555-0100"
        contact.address1() == "1 Test St"
        contact.address2() == "Suite 2"
        contact.city() == "Testville"
        contact.state() == "CA"
        contact.postalCode() == "90000"
        contact.country() == "USA"
    }

    def "getMyContact: mapperがnull(該当行なし)を返した場合はResourceNotFoundExceptionになる"() {
        given:
        accountContactCustomMapper.findByUserId(USER_ID) >> null

        when:
        service.getMyContact()

        then:
        thrown(ResourceNotFoundException)
    }

    def "getMyContact: 未認証(CurrentUserProviderが空)はAccessDeniedExceptionになる"() {
        given:
        def unauthenticatedProvider = Stub(CurrentUserProvider) {
            requireCurrentUser() >> { throw new AccessDeniedException("Authentication required") }
        }
        def unauthenticatedService = new AccountApplicationService(accountContactCustomMapper, unauthenticatedProvider)

        when:
        unauthenticatedService.getMyContact()

        then:
        thrown(AccessDeniedException)
        0 * accountContactCustomMapper.findByUserId(_)
    }
}
