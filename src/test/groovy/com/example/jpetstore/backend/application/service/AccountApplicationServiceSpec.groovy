package com.example.jpetstore.backend.application.service

import com.example.jpetstore.backend.domain.account.AccountContact
import com.example.jpetstore.backend.domain.account.AccountEditCommand
import com.example.jpetstore.backend.domain.account.AccountRepository
import com.example.jpetstore.backend.domain.account.AccountUpdate
import com.example.jpetstore.backend.domain.account.PasswordChangeCommand
import com.example.jpetstore.backend.domain.account.UserPreferences
import com.example.jpetstore.backend.domain.exception.InvalidCurrentPasswordException
import com.example.jpetstore.backend.domain.exception.OptimisticLockConflictException
import com.example.jpetstore.backend.domain.exception.ResourceNotFoundException
import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.domain.security.CurrentUserProvider
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.crypto.password.PasswordEncoder
import spock.lang.Specification

/**
 * #7/#30: プリフィル用の自分自身の氏名/連絡先/住所を返すユースケース（read-only・計画フェーズ確定①）を検証する。
 *
 * <p>{@code CurrentUserProvider.requireCurrentUser()} でprincipal→userIdを解決する（クライアント入力のuserIdは
 * 一切受け取らない＝IDOR面ゼロ）。#30でAccountContactCustomMapper直呼びからAccountRepository（Domain層）経由へ
 * retrofitした（backend-conventions §9・DB非依存Repositoryモック）。
 *
 * <p>#14: {@code getAccountForEdit}（version込みプリフィル）/{@code updateAccount}（version楽観ロック・
 * AC-neg1〜3）を検証する。本人固定はCurrentUserProvider起点のuserIdのみを使う（クライアントのuserId受理なし）。
 */
class AccountApplicationServiceSpec extends Specification {

    private static final Long USER_ID = 42L

    AccountRepository accountRepository = Mock()
    CurrentUserProvider currentUserProvider = Stub() {
        requireCurrentUser() >> new AuthenticatedUser(USER_ID, "account_user", ["USER"])
    }
    PasswordEncoder passwordEncoder = Mock()
    AuthApplicationService authApplicationService = Mock()

    AccountApplicationService service = new AccountApplicationService(
            accountRepository, currentUserProvider, passwordEncoder, authApplicationService)

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
        def unauthenticatedService = new AccountApplicationService(accountRepository, unauthenticatedProvider, passwordEncoder, authApplicationService)

        when:
        unauthenticatedService.getMyContact()

        then:
        thrown(AccessDeniedException)
        0 * accountRepository.findContactByUserId(_)
    }

    def "getPreferences: 引数のuserIdでaccountRepositoryを呼びUserPreferencesを返す(#36/#25・CurrentUserProvider不使用)"() {
        when:
        def result = service.getPreferences(USER_ID)

        then:
        1 * accountRepository.findPreferencesByUserId(USER_ID) >> Optional.of(new UserPreferences("dark", "japanese"))
        result.colorSchemePreference() == "dark"
        result.languagePreference() == "japanese"
    }

    def "getPreferences: repositoryがOptional.empty()(m_profile行なし)を返した場合は既定値(system/english)へフォールバックする(例外を投げない)"() {
        given:
        accountRepository.findPreferencesByUserId(USER_ID) >> Optional.empty()

        when:
        def result = service.getPreferences(USER_ID)

        then:
        result.colorSchemePreference() == "system"
        result.languagePreference() == "english"
    }

    private static com.example.jpetstore.backend.domain.account.AccountEditDetail editDetail(long version = 3L) {
        new com.example.jpetstore.backend.domain.account.AccountEditDetail(
                "Taro", "Yamada", "taro@example.com", "555-0100",
                "1 Test St", "Suite 2", "Testville", "CA", "90000", "USA",
                "english", "FISH", "dark", version)
    }

    def "getAccountForEdit: CurrentUserProviderのuserIdでfindEditDetailByUserIdを呼びversion込みで返す(E3)"() {
        when:
        def result = service.getAccountForEdit()

        then:
        1 * accountRepository.findEditDetailByUserId(USER_ID) >> Optional.of(editDetail())
        result.firstName() == "Taro"
        result.languagePreference() == "english"
        result.favoriteCategoryId() == "FISH"
        result.colorSchemePreference() == "dark"
        result.version() == 3L
    }

    def "getAccountForEdit: repositoryがOptional.empty()を返した場合はResourceNotFoundException"() {
        given:
        accountRepository.findEditDetailByUserId(USER_ID) >> Optional.empty()

        when:
        service.getAccountForEdit()

        then:
        thrown(ResourceNotFoundException)
    }

    private static AccountEditCommand editCommand(long expectedVersion = 3L) {
        new AccountEditCommand(
                expectedVersion, "Taro", "Yamada", "taro2@example.com", "555-0199",
                "2 New St", null, "Newtown", "NY", "10001", "USA",
                "japanese", "DOGS", "light")
    }

    def "updateAccount: CurrentUserProvider起点のuserIdをAccountUpdateへ渡す(AC1本人固定・usernameをクライアントから受けない)"() {
        when:
        service.updateAccount(editCommand())

        then:
        1 * accountRepository.updateAccount({ AccountUpdate u ->
            u.userId() == USER_ID &&
                    u.expectedVersion() == 3L &&
                    u.firstName() == "Taro" &&
                    u.lastName() == "Yamada" &&
                    u.email() == "taro2@example.com" &&
                    u.phone() == "555-0199" &&
                    u.address1() == "2 New St" &&
                    u.address2() == null &&
                    u.city() == "Newtown" &&
                    u.state() == "NY" &&
                    u.postalCode() == "10001" &&
                    u.country() == "USA" &&
                    u.languagePreference() == "japanese" &&
                    u.favoriteCategoryId() == "DOGS" &&
                    u.colorSchemePreference() == "light"
        }) >> 1
    }

    def "updateAccount: 成功時はexpectedVersion+1をversionとして持つAccountEditDetailを返す(再読取りなしでecho)"() {
        given:
        accountRepository.updateAccount(_) >> 1

        when:
        def result = service.updateAccount(editCommand(3L))

        then:
        result.version() == 4L
        result.firstName() == "Taro"
        result.email() == "taro2@example.com"
        result.languagePreference() == "japanese"
        result.favoriteCategoryId() == "DOGS"
        result.colorSchemePreference() == "light"
        0 * accountRepository.findEditDetailByUserId(_)
    }

    def "updateAccount: affected=0(楽観ロック競合)はOptimisticLockConflictException(409・AC-neg3)"() {
        given:
        accountRepository.updateAccount(_) >> 0

        when:
        service.updateAccount(editCommand())

        then:
        thrown(OptimisticLockConflictException)
    }

    def "updateAccount: 未認証(CurrentUserProviderが空)はAccessDeniedExceptionになりrepositoryは呼ばれない"() {
        given:
        def unauthenticatedProvider = Stub(CurrentUserProvider) {
            requireCurrentUser() >> { throw new AccessDeniedException("Authentication required") }
        }
        def unauthenticatedService = new AccountApplicationService(accountRepository, unauthenticatedProvider, passwordEncoder, authApplicationService)

        when:
        unauthenticatedService.updateAccount(editCommand())

        then:
        thrown(AccessDeniedException)
        0 * accountRepository.updateAccount(_)
    }

    private static PasswordChangeCommand passwordChangeCommand(
            String currentPassword = "OldCorrect#1", String newPassword = "NewCorrect#2") {
        new PasswordChangeCommand(currentPassword, newPassword)
    }

    def "changePassword: 現在PWがhashと一致すれば新PWをbcryptエンコードしupdatePasswordへ渡し、トークンをローテートする(AC1/AC2/Q3)"() {
        given:
        HttpServletResponse response = Mock()

        when:
        service.changePassword(passwordChangeCommand(), response)

        then:
        1 * accountRepository.findPasswordHashByUserId(USER_ID) >> Optional.of("{bcrypt}stored-hash")
        1 * passwordEncoder.matches("OldCorrect#1", "{bcrypt}stored-hash") >> true
        1 * passwordEncoder.encode("NewCorrect#2") >> "{bcrypt}new-hash"
        1 * accountRepository.updatePassword(USER_ID, "{bcrypt}new-hash")
        1 * authApplicationService.issueTokensFor({ AuthenticatedUser u -> u.userId() == USER_ID }, response)
    }

    def "changePassword: 現在PWが不一致ならInvalidCurrentPasswordExceptionになり、更新もトークン発行もしない(AC-neg1・422)"() {
        given:
        HttpServletResponse response = Mock()
        accountRepository.findPasswordHashByUserId(USER_ID) >> Optional.of("{bcrypt}stored-hash")
        passwordEncoder.matches("WrongCurrent#1", "{bcrypt}stored-hash") >> false

        when:
        service.changePassword(passwordChangeCommand("WrongCurrent#1", "NewCorrect#2"), response)

        then:
        thrown(InvalidCurrentPasswordException)
        0 * accountRepository.updatePassword(_, _)
        0 * authApplicationService.issueTokensFor(_, _)
    }

    def "changePassword: repositoryがOptional.empty()(m_signon行なし)を返した場合はResourceNotFoundException"() {
        given:
        HttpServletResponse response = Mock()
        accountRepository.findPasswordHashByUserId(USER_ID) >> Optional.empty()

        when:
        service.changePassword(passwordChangeCommand(), response)

        then:
        thrown(ResourceNotFoundException)
        0 * accountRepository.updatePassword(_, _)
        0 * authApplicationService.issueTokensFor(_, _)
    }

    def "changePassword: 未認証(CurrentUserProviderが空)はAccessDeniedExceptionになりrepositoryは呼ばれない"() {
        given:
        HttpServletResponse response = Mock()
        def unauthenticatedProvider = Stub(CurrentUserProvider) {
            requireCurrentUser() >> { throw new AccessDeniedException("Authentication required") }
        }
        def unauthenticatedService = new AccountApplicationService(accountRepository, unauthenticatedProvider, passwordEncoder, authApplicationService)

        when:
        unauthenticatedService.changePassword(passwordChangeCommand(), response)

        then:
        thrown(AccessDeniedException)
        0 * accountRepository.findPasswordHashByUserId(_)
        0 * accountRepository.updatePassword(_, _)
    }
}
