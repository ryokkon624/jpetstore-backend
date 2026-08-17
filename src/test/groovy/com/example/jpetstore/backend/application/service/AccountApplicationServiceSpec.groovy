package com.example.jpetstore.backend.application.service

import com.example.jpetstore.backend.domain.account.AccountContact
import com.example.jpetstore.backend.domain.account.AccountRepository
import com.example.jpetstore.backend.domain.exception.ResourceNotFoundException
import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.domain.security.CurrentUserProvider
import org.springframework.security.access.AccessDeniedException
import spock.lang.Specification

/**
 * #7/#30: プリフィル用の自分自身の氏名/連絡先/住所を返すユースケース（read-only・計画フェーズ確定①）を検証する。
 *
 * <p>{@code CurrentUserProvider.requireCurrentUser()} でprincipal→userIdを解決する（クライアント入力のuserIdは
 * 一切受け取らない＝IDOR面ゼロ）。#30でAccountContactCustomMapper直呼びからAccountRepository（Domain層）経由へ
 * retrofitした（backend-conventions §9・DB非依存Repositoryモック）。
 */
class AccountApplicationServiceSpec extends Specification {

    private static final Long USER_ID = 42L

    AccountRepository accountRepository = Mock()
    CurrentUserProvider currentUserProvider = Stub() {
        requireCurrentUser() >> new AuthenticatedUser(USER_ID, "account_user", ["USER"])
    }

    AccountApplicationService service = new AccountApplicationService(accountRepository, currentUserProvider)

    private static AccountContact contact() {
        new AccountContact(
                "Taro", "Yamada", "taro@example.com", "555-0100",
                "1 Test St", "Suite 2", "Testville", "CA", "90000", "USA")
    }

    def "getMyContact: CurrentUserProviderのuserIdでaccountRepositoryを呼びAccountContactを返す"() {
        when:
        def result = service.getMyContact()

        then:
        1 * accountRepository.findContactByUserId(USER_ID) >> Optional.of(contact())
        result.firstName() == "Taro"
        result.lastName() == "Yamada"
        result.email() == "taro@example.com"
        result.phone() == "555-0100"
        result.address1() == "1 Test St"
        result.address2() == "Suite 2"
        result.city() == "Testville"
        result.state() == "CA"
        result.postalCode() == "90000"
        result.country() == "USA"
    }

    def "getMyContact: repositoryがOptional.empty()(該当行なし)を返した場合はResourceNotFoundExceptionになる"() {
        given:
        accountRepository.findContactByUserId(USER_ID) >> Optional.empty()

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
        def unauthenticatedService = new AccountApplicationService(accountRepository, unauthenticatedProvider)

        when:
        unauthenticatedService.getMyContact()

        then:
        thrown(AccessDeniedException)
        0 * accountRepository.findContactByUserId(_)
    }
}
