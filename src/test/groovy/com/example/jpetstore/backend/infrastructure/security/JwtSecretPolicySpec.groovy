package com.example.jpetstore.backend.infrastructure.security

import com.example.jpetstore.backend.support.TestJwtSecrets
import spock.lang.Specification
import spock.lang.Unroll

/**
 * #38 AC1/AC3: {@link JwtSecretPolicy} の denylist・最小エントロピー検証・例外メッセージの単体テスト。
 *
 * <p>DB非依存の高速な単体テストとして、{@code JwtProperties}（コンテキスト起動）とは分離して
 * 境界値・denylistの網羅を担う。出典: L3 N1（{@code reports/after/l3-security-regression-backend.md}
 * §2.1・SEC run {@code security/20260819_01}）。
 */
class JwtSecretPolicySpec extends Specification {

    private static final List<String> DENYLIST_VALUES = [
            "please-replace-with-a-random-secret-of-at-least-32-bytes",
            "changeme",
            "change-me",
            "changeit",
            "change-it",
            "changethis",
            "replaceme",
            "replace-me",
            "secret",
            "mysecret",
            "supersecret",
            "topsecret",
            "jwtsecret",
            "jwt-secret",
            "jwt_secret",
            "your-secret-key",
            "my-secret-key",
            "password",
            "passw0rd",
            "default",
            "example",
            "placeholder",
            "dummy",
            "todo",
            "test",
            "testing",
            "dev",
            "development",
    ]

    @Unroll
    def "denylist値「#secret」は例外を投げる"() {
        when:
        JwtSecretPolicy.validate(secret)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("placeholder")

        where:
        secret << DENYLIST_VALUES
    }

    @Unroll
    def "denylistはtrim・大文字小文字非依存の完全一致で判定する(#input)"() {
        when:
        JwtSecretPolicy.validate(input)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("placeholder")

        where:
        input << [
                "  please-replace-with-a-random-secret-of-at-least-32-bytes  ",
                "PLEASE-REPLACE-WITH-A-RANDOM-SECRET-OF-AT-LEAST-32-BYTES",
                "CHANGEME",
                "  ChangeMe  ",
        ]
    }

    def ".env.example配布値(56byte)はdenylistで拒否される(L3 N1根因)"() {
        when:
        JwtSecretPolicy.validate(TestJwtSecrets.ENV_EXAMPLE_PLACEHOLDER)

        then:
        thrown(IllegalStateException)
    }

    def "32byte未満(denylist非該当)は鍵長不足の例外を投げる"() {
        when:
        JwtSecretPolicy.validate("q" * 31)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("32")
        !ex.message.contains("placeholder")
    }

    def "32byte以上でもユニーク文字数24未満なら例外を投げる(補助エントロピー検証)"() {
        when:
        JwtSecretPolicy.validate(TestJwtSecrets.LOW_ENTROPY)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("distinct characters")
        ex.message.contains("24")
    }

    def "32byte以上かつユニーク文字数24以上なら例外を投げない"() {
        when:
        JwtSecretPolicy.validate(TestJwtSecrets.STRONG)

        then:
        noExceptionThrown()
    }

    def "denylist該当は8byteのような短い値でも鍵長不足メッセージにはならない(denylistが優先)"() {
        when:
        JwtSecretPolicy.validate("changeme")

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("placeholder")
        !ex.message.contains("must be at least")
    }

    def "例外メッセージに秘密の値そのものを含めない(AC3)"() {
        given:
        def lowEntropySecret = "z" * 40

        when:
        JwtSecretPolicy.validate(lowEntropySecret)

        then:
        def ex = thrown(IllegalStateException)
        !ex.message.contains(lowEntropySecret)
    }
}
