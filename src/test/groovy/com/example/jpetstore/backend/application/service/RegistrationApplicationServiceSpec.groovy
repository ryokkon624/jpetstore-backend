package com.example.jpetstore.backend.application.service

import com.example.jpetstore.backend.domain.account.AccountRepository
import com.example.jpetstore.backend.domain.account.NewAccountRegistration
import com.example.jpetstore.backend.domain.account.RegisterAccountCommand
import com.example.jpetstore.backend.domain.exception.UsernameAlreadyExistsException
import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.infrastructure.security.RegisterAttemptService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.crypto.password.PasswordEncoder
import spock.lang.Specification

/**
 * #13 AC1/AC3/AC4・AC-neg1/AC-neg2: ユーザー登録ユースケース（DB-backedレート制限ゲート→
 * password一致検証→bcryptエンコード→m_account/m_signon/m_profile INSERT→username重複409→
 * 自動ログイン(fresh JWT)）を検証する。
 *
 * <p>E9: 自動ログインは{@code AuthApplicationService#issueTokensFor}（login末尾から抽出）を再利用する
 * （照合はスキップ・新規作成した本人であることは登録処理自体が保証している）。
 *
 * <p>レート制限: {@code assertNotRateLimited}がロック中(429相当)なら即座に短絡し、以降の処理を一切行わない
 * （{@code LoginAttemptService}と同じ設計）。それ以外は結果（成功/400/409）を問わず必ず{@code recordAttempt}
 * を呼ぶ（登録は「1回の試行」自体がIP由来の資源消費であるため。implementation-notes.md参照）。
 */
class RegistrationApplicationServiceSpec extends Specification {

    private static final String CLIENT_IP = "203.0.113.9"

    AccountRepository accountRepository = Mock()
    PasswordEncoder passwordEncoder = Mock()
    RegisterAttemptService registerAttemptService = Mock()
    AuthApplicationService authApplicationService = Mock()
    HttpServletRequest request = Stub() {
        getRemoteAddr() >> CLIENT_IP
    }
    HttpServletResponse response = Mock()

    RegistrationApplicationService service = new RegistrationApplicationService(
            accountRepository, passwordEncoder, registerAttemptService, authApplicationService)

    private static RegisterAccountCommand command(
            String password = "correct-horse", String repeatedPassword = "correct-horse",
            String languagePreference = null, String favoriteCategoryId = null) {
        new RegisterAccountCommand(
                "new_user", password, repeatedPassword, "new_user@example.com", "Taro", "Yamada",
                "1 Test St", "Suite 2", "Testville", "CA", "90000", "USA", "555-0100",
                languagePreference, favoriteCategoryId)
    }

    def "レート制限中ならassertNotRateLimitedで短絡し、以降の処理を一切行わない"() {
        given:
        registerAttemptService.assertNotRateLimited(CLIENT_IP) >> { throw new com.example.jpetstore.backend.domain.exception.RegistrationRateLimitExceededException() }

        when:
        service.register(command(), request, response)

        then:
        thrown(com.example.jpetstore.backend.domain.exception.RegistrationRateLimitExceededException)
        0 * passwordEncoder.encode(_)
        0 * accountRepository.register(_)
        0 * registerAttemptService.recordAttempt(_)
        0 * authApplicationService.issueTokensFor(_, _)
    }

    def "パスワード不一致はIllegalArgumentExceptionになり、attemptは記録される(レート制限ゲートは通過済のため)"() {
        when:
        service.register(command("pw-a", "pw-b"), request, response)

        then:
        thrown(IllegalArgumentException)
        1 * registerAttemptService.assertNotRateLimited(CLIENT_IP)
        1 * registerAttemptService.recordAttempt(CLIENT_IP)
        0 * accountRepository.register(_)
        0 * authApplicationService.issueTokensFor(_, _)
    }

    def "正常系: bcryptエンコードしaccountRepository.registerへ渡し、fresh JWTで自動ログインする"() {
        given:
        passwordEncoder.encode("correct-horse") >> "{bcrypt}hashed"

        when:
        def result = service.register(command(), request, response)

        then:
        1 * registerAttemptService.assertNotRateLimited(CLIENT_IP)
        1 * accountRepository.register({ NewAccountRegistration r ->
            r.username() == "new_user" &&
                    r.passwordHash() == "{bcrypt}hashed" &&
                    r.email() == "new_user@example.com" &&
                    r.firstName() == "Taro" &&
                    r.lastName() == "Yamada" &&
                    r.address1() == "1 Test St" &&
                    r.address2() == "Suite 2" &&
                    r.city() == "Testville" &&
                    r.state() == "CA" &&
                    r.postalCode() == "90000" &&
                    r.country() == "USA" &&
                    r.phone() == "555-0100" &&
                    r.languagePreference() == "english" &&
                    r.favoriteCategoryId() == null
        }) >> 99L
        1 * authApplicationService.issueTokensFor({ AuthenticatedUser u ->
            u.userId() == 99L && u.username() == "new_user" && u.roles() == ["USER"]
        }, response)
        1 * registerAttemptService.recordAttempt(CLIENT_IP)
        result.userId() == 99L
        result.username() == "new_user"
        result.roles() == ["USER"]
    }

    def "languagePreference/favoriteCategoryIdが指定されていればそのまま渡す(E5・DTOはoptionalで受理)"() {
        given:
        passwordEncoder.encode(_) >> "{bcrypt}hashed"

        when:
        service.register(command("correct-horse", "correct-horse", "japanese", "FISH"), request, response)

        then:
        1 * accountRepository.register({ NewAccountRegistration r ->
            r.languagePreference() == "japanese" && r.favoriteCategoryId() == "FISH"
        }) >> 1L
    }

    def "username重複(DuplicateKeyException)はUsernameAlreadyExistsExceptionに変換し、自動ログインは行わない。attemptは記録される"() {
        given:
        passwordEncoder.encode(_) >> "{bcrypt}hashed"
        accountRepository.register(_) >> { throw new DuplicateKeyException("uk_m_account_username") }

        when:
        service.register(command(), request, response)

        then:
        thrown(UsernameAlreadyExistsException)
        1 * registerAttemptService.recordAttempt(CLIENT_IP)
        0 * authApplicationService.issueTokensFor(_, _)
    }

    def "clientIpはHttpServletRequest#getRemoteAddrから解決する(X-Forwarded-Forは信頼しない)"() {
        given:
        passwordEncoder.encode(_) >> "{bcrypt}hashed"
        accountRepository.register(_) >> 1L

        when:
        service.register(command(), request, response)

        then:
        1 * registerAttemptService.assertNotRateLimited(CLIENT_IP)
        1 * registerAttemptService.recordAttempt(CLIENT_IP)
    }
}
