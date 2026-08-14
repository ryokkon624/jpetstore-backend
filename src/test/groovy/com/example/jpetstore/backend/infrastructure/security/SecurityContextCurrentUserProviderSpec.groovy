package com.example.jpetstore.backend.infrastructure.security

import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification

class SecurityContextCurrentUserProviderSpec extends Specification {

    def provider = new SecurityContextCurrentUserProvider()

    void cleanup() {
        SecurityContextHolder.clearContext()
    }

    def "SecurityContextにAuthenticatedUserが認証済みで入っていればそれを返す"() {
        given:
        def user = new AuthenticatedUser(1L, "j2ee", ["USER"])
        def authentication = new UsernamePasswordAuthenticationToken(
                user, null, [new SimpleGrantedAuthority("ROLE_USER")])
        SecurityContextHolder.getContext().setAuthentication(authentication)

        expect:
        provider.currentUser() == Optional.of(user)
        provider.requireCurrentUser() == user
    }

    def "未認証(Authenticationがnull)なら空を返す"() {
        expect:
        provider.currentUser() == Optional.empty()
    }

    def "匿名認証(AnonymousAuthenticationToken)なら空を返す"() {
        given:
        def anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", [new SimpleGrantedAuthority("ROLE_ANONYMOUS")])
        SecurityContextHolder.getContext().setAuthentication(anonymous)

        expect:
        provider.currentUser() == Optional.empty()
    }

    def "未認証状態でrequireCurrentUserを呼ぶとAccessDeniedExceptionを投げる"() {
        when:
        provider.requireCurrentUser()

        then:
        thrown(AccessDeniedException)
    }
}
