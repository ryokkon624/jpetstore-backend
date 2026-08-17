package com.example.jpetstore.backend.application.service

import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.infrastructure.security.AuthCookieSupport
import com.example.jpetstore.backend.infrastructure.security.AuthenticatedUserDetails
import com.example.jpetstore.backend.infrastructure.security.JwtProperties
import com.example.jpetstore.backend.infrastructure.security.JwtService
import com.example.jpetstore.backend.infrastructure.security.LoginAttemptService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import spock.lang.Specification

import java.time.Duration

/**
 * #20 AC1(SBD-6): レート制限/ロックアウトの前段ゲートが {@code AuthApplicationService.login} に
 * 正しく結線されていることを検証する（{@code LoginAttemptService} 自体のロジックは {@code
 * LoginAttemptServiceSpec} で検証済み。ここでは呼び出し順序・例外伝播のみを検証する）。
 */
class AuthApplicationServiceSpec extends Specification {

    AuthenticationManager authenticationManager = Mock()
    JwtService jwtService = Mock()
    AuthCookieSupport cookieSupport = Mock()
    JwtProperties jwtProperties = Stub() {
        accessTokenTtl() >> Duration.ofMinutes(15)
        refreshTokenTtl() >> Duration.ofDays(7)
    }
    LoginAttemptService loginAttemptService = Mock()
    HttpServletResponse response = Mock()

    AuthApplicationService service = new AuthApplicationService(
            authenticationManager, jwtService, cookieSupport, jwtProperties, loginAttemptService)

    def "ロック中ならauthenticate()を呼ばずに短絡し、既存の誤資格と同一の例外をそのまま伝播する"() {
        given:
        loginAttemptService.assertNotLocked("locked_user") >> { throw new BadCredentialsException("locked") }

        when:
        service.login("locked_user", "whatever", response)

        then:
        thrown(BadCredentialsException)
        0 * authenticationManager.authenticate(_)
        0 * loginAttemptService.recordFailure(_)
    }

    def "authenticate失敗時はrecordFailureを呼び例外をそのまま伝播する"() {
        given:
        authenticationManager.authenticate(_ as UsernamePasswordAuthenticationToken) >> {
            throw new BadCredentialsException("bad creds")
        }

        when:
        service.login("someone", "wrong", response)

        then:
        thrown(BadCredentialsException)
        1 * loginAttemptService.assertNotLocked("someone")
        1 * loginAttemptService.recordFailure("someone")
        0 * loginAttemptService.recordSuccess(_)
    }

    def "authenticate成功時はrecordSuccessを呼びトークンを発行する"() {
        given:
        def user = new AuthenticatedUser(1L, "someone", ["USER"])
        def principal = new AuthenticatedUserDetails(user, "hash")
        Authentication authResult = Stub() {
            getPrincipal() >> principal
        }
        authenticationManager.authenticate(_ as UsernamePasswordAuthenticationToken) >> authResult
        jwtService.generateAccessToken(user) >> "access-token"
        jwtService.generateRefreshToken(user) >> "refresh-token"

        when:
        def result = service.login("someone", "correct", response)

        then:
        result == user
        1 * loginAttemptService.assertNotLocked("someone")
        1 * loginAttemptService.recordSuccess("someone")
        1 * cookieSupport.writeAccessTokenCookie(response, "access-token", Duration.ofMinutes(15))
        1 * cookieSupport.writeRefreshTokenCookie(response, "refresh-token", Duration.ofDays(7))
    }

    def "issueTokensFor: 照合を経ずにaccess/refreshを新規発行してCookieへ書き込む(#13 E9・登録の自動ログインが再利用)"() {
        given:
        def user = new AuthenticatedUser(7L, "new_user", ["USER"])
        jwtService.generateAccessToken(user) >> "new-access-token"
        jwtService.generateRefreshToken(user) >> "new-refresh-token"

        when:
        service.issueTokensFor(user, response)

        then:
        0 * authenticationManager.authenticate(_)
        1 * cookieSupport.writeAccessTokenCookie(response, "new-access-token", Duration.ofMinutes(15))
        1 * cookieSupport.writeRefreshTokenCookie(response, "new-refresh-token", Duration.ofDays(7))
    }
}
