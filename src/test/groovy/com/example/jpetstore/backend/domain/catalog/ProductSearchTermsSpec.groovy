package com.example.jpetstore.backend.domain.catalog

import spock.lang.Specification

/**
 * #2 AC1/[L2]/ID-29: 検索キーワードを語分割し、LIKE用にエスケープ済みパターンへ変換する純VO。
 *
 * <p>語分割は as-is（legacy {@code SqlMapProductDao.ProductSearch}）と同じ単一スペース区切りを踏襲する
 * （[L2] 旧同値）。各語は LIKE メタ文字（{@code %}/{@code _}/{@code \}）をリテラル化してから {@code %}...{@code %}
 * で囲む（ID-29・ハードニング）。SQLi は呼び出し側で {@code #{}} パラメタライズするため、このVOは文字列operating
 * のみでSQL文字列を組み立てない（AC-neg1）。
 */
class ProductSearchTermsSpec extends Specification {

    def "[L2] 単一キーワードは%で囲んだ1パターンになる"() {
        expect:
        ProductSearchTerms.from("dog").patterns() == ["%dog%"]
    }

    def "[L2] 複数キーワードは単一スペース区切りで語分割され、各語が%で囲まれる(as-is踏襲)"() {
        expect:
        ProductSearchTerms.from("fish dog").patterns() == ["%fish%", "%dog%"]
    }

    def "AC2: null/空文字/空白のみのキーワードは空パターンになる(空結果への正規化に使う)"() {
        expect:
        ProductSearchTerms.from(keyword).isEmpty()

        where:
        keyword << [null, "", "   "]
    }

    def "ID-29: LIKEメタ文字%/_/\\はバックスラッシュでエスケープされリテラル化される"() {
        expect:
        ProductSearchTerms.from(keyword).patterns() == [expectedPattern]

        where:
        keyword  | expectedPattern
        "100%"   | "%100\\%%"
        "a_b"    | "%a\\_b%"
        "a\\b"   | "%a\\\\b%"
    }

    def "AC-neg1: SQLメタ文字を含むキーワードも語分割後それぞれリテラルなLIKEパターンとして扱われる(SQL文字列を組み立てない)"() {
        expect:
        ProductSearchTerms.from("' OR 1=1 --").patterns() == ["%'%", "%OR%", "%1=1%", "%--%"]
    }
}
