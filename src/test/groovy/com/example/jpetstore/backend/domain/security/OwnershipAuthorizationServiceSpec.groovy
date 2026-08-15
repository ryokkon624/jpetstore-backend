package com.example.jpetstore.backend.domain.security

import org.springframework.security.access.AccessDeniedException
import spock.lang.Specification

/**
 * 本人スコープ（所有者一致）認可の再利用可能な部品（#21 AC1/SBD-1）。
 *
 * {@link CurrentUserProvider} のみを identity 源とし、呼び出し元が渡す「サーバー側で解決済みの
 * 真の所有者 userId」と比較する。リクエストの form/param 値を直接判定に使わないことを
 * このガード自身のシグネチャ（引数は resourceOwnerUserId のみ）で構造的に保証する。
 */
class OwnershipAuthorizationServiceSpec extends Specification {

    CurrentUserProvider currentUserProvider = Mock()
    OwnershipAuthorizationService service = new OwnershipAuthorizationService(currentUserProvider)

    def "所有者(userId一致)なら例外を投げない"() {
        given:
        currentUserProvider.requireCurrentUser() >> new AuthenticatedUser(1L, "owner", ["USER"])

        when:
        service.assertOwner(1L)

        then:
        noExceptionThrown()
    }

    def "非所有者(userId不一致)ならAccessDeniedExceptionを投げる"() {
        given:
        currentUserProvider.requireCurrentUser() >> new AuthenticatedUser(1L, "attacker", ["USER"])

        when:
        service.assertOwner(999L)

        then:
        thrown(AccessDeniedException)
    }

    def "未認証ならAccessDeniedExceptionを投げる(CurrentUserProvider#requireCurrentUserの既定動作を利用)"() {
        given:
        currentUserProvider.requireCurrentUser() >> { throw new AccessDeniedException("Authentication required") }

        when:
        service.assertOwner(1L)

        then:
        thrown(AccessDeniedException)
    }
}
