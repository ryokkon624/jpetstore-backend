package com.example.jpetstore.backend.config

import org.springframework.security.crypto.password.PasswordEncoder
import spock.lang.Specification

/**
 * パスワードをbcryptでハッシュ＋ソルト保存・照合する基盤の単体テスト（#19 AC1）。
 *
 * <p>Spring管理のBeanを起動する必要が無いため、素のSpock単体テストとして {@link PasswordEncoderConfig}
 * を直接インスタンス化して検証する（Testcontainers/Spring Contextは使わない）。
 */
class PasswordEncoderConfigSpec extends Specification {

    def encoder = new PasswordEncoderConfig().passwordEncoder()

    def "encodeした結果は平文と一致しない(AC1: 平文保存しない)"() {
        given:
        def raw = "correct horse battery staple"

        expect:
        encoder.encode(raw) != raw
    }

    def "encodeした結果はbcrypt形式である(AC1: bcryptでハッシュ)"() {
        given:
        def raw = "correct horse battery staple"

        when:
        def encoded = encoder.encode(raw)

        then:
        // DelegatingPasswordEncoder は {bcrypt}$2a$... の形式でIDプレフィックスを付与する。
        encoded.startsWith("{bcrypt}\$2")
    }

    def "同じ平文でも呼ぶたびに異なるハッシュになる(ソルト付与の確認)"() {
        given:
        def raw = "correct horse battery staple"

        expect:
        encoder.encode(raw) != encoder.encode(raw)
    }

    def "matchesは正しい平文に対してtrueを返す(照合の往復確認)"() {
        given:
        def raw = "correct horse battery staple"
        def encoded = encoder.encode(raw)

        expect:
        encoder.matches(raw, encoded)
    }

    def "matchesは誤った平文に対してfalseを返す"() {
        given:
        def encoded = encoder.encode("correct horse battery staple")

        expect:
        !encoder.matches("wrong password", encoded)
    }

    def "passwordEncoderはPasswordEncoder型のBeanとして提供される"() {
        expect:
        encoder instanceof PasswordEncoder
    }
}
