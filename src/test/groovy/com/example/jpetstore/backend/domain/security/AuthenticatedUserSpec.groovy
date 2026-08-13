package com.example.jpetstore.backend.domain.security

import spock.lang.Specification

class AuthenticatedUserSpec extends Specification {

    def "roles未指定(null)なら空リストになる"() {
        when:
        def user = new AuthenticatedUser(1L, "j2ee", null)

        then:
        user.roles() == []
    }

    def "rolesは不変リストとして保持される(呼び出し元での書き換え不可)"() {
        given:
        def mutableRoles = new ArrayList<String>(["USER"])

        when:
        def user = new AuthenticatedUser(1L, "j2ee", mutableRoles)
        mutableRoles.add("ADMIN")

        then:
        user.roles() == ["USER"]
    }

    def "userId/usernameをそのまま保持する"() {
        when:
        def user = new AuthenticatedUser(42L, "j2ee", ["USER"])

        then:
        user.userId() == 42L
        user.username() == "j2ee"
        user.roles() == ["USER"]
    }
}
