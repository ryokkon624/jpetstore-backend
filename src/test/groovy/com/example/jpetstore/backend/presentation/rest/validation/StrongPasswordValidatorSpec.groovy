package com.example.jpetstore.backend.presentation.rest.validation

import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * #15/#17 Q1確定: PW強度共有制約(8〜72文字・UTF-8 72バイト以下・英大/英小/数字/記号の4種中2種以上)を検証する。
 *
 * <p>{@code register.password}(#17)と{@code PasswordChangeRequest.newPassword}(#15)の両方が本Validatorを
 * 共有する(仕様分岐防止・計画フェーズ確定)。{@code ConstraintValidatorContext}はisValid内で未使用のため
 * nullを渡すplain Spock(Spring context不要)で検証する。
 */
class StrongPasswordValidatorSpec extends Specification {

    StrongPasswordValidator validator = new StrongPasswordValidator()

    def "isValid: 8〜72文字・4種中2種以上を満たす値はtrue(#value)"() {
        expect:
        validator.isValid(value, null)

        where:
        value << [
                "Correct#Passw0rd!", // 大/小/数字/記号すべて
                "abcdefgH",           // ちょうど8文字・小文字+大文字の2種
                "password1",          // 小文字+数字の2種
                "PASSWORD1",          // 大文字+数字の2種
                "a" * 70 + "B1",      // 72文字ちょうど・2種
        ]
    }

    def "isValid: null/空文字はfalse"() {
        expect:
        !validator.isValid(value, null)

        where:
        value << [null, ""]
    }

    def "isValid: 8文字未満はfalse(#value.length()文字)"() {
        expect:
        !validator.isValid(value, null)

        where:
        value << ["Ab1", "Abcdef1"] // 3文字・7文字
    }

    def "isValid: 73文字(長さ超過)はfalse"() {
        given:
        def value = "a" * 71 + "B1" // 73文字

        expect:
        !validator.isValid(value, null)
    }

    def "isValid: 文字数は72以下でもUTF-8バイト長が72を超える場合はfalse(bcrypt 72バイト上限・暗黙切り詰め防止)"() {
        given: "マルチバイト文字(例: 全角「あ」=3バイト)を含み、文字数は72だがバイト長は72を超える"
        def value = ("あ" * 24) + "Ab1234567890" // 24*3=72バイト + 13バイト = 85バイト、文字数は37文字
        assert value.length() <= 72
        assert value.getBytes(StandardCharsets.UTF_8).length > 72

        expect:
        !validator.isValid(value, null)
    }

    def "isValid: 文字種が1種類のみはfalse(#value)"() {
        expect:
        !validator.isValid(value, null)

        where:
        value << [
                "alllowercase",  // 小文字のみ
                "ALLUPPERCASE",  // 大文字のみ
                "12345678",      // 数字のみ
                "!!!!!!!!",      // 記号のみ
        ]
    }

    def "isValid: 文字種が2種類あればtrue(#value)"() {
        expect:
        validator.isValid(value, null)

        where:
        value << [
                "abcdefgh1",   // 小文字+数字
                "ABCDEFGH!",   // 大文字+記号
                "12345678!",   // 数字+記号
        ]
    }
}
