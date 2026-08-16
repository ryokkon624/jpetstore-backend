package com.example.jpetstore.backend.domain.common

import spock.lang.Specification

/** #1 論点3: totalPages算出(切り上げ・1-index)を検証する。 */
class PageSpec extends Specification {

    def "of()はtotalElementsとsizeからtotalPagesを切り上げで算出する(size=2, DOGS6商品→3頁)"() {
        given:
        def p = Page.of(["K9-BD-01", "K9-PO-02"], 1, 2, 6)

        expect:
        p.totalPages() == 3
        p.page() == 1
        p.size() == 2
        p.totalElements() == 6
    }

    def "端数があるtotalElementsは切り上げでtotalPagesになる(例: 4件size3→2頁)"() {
        expect:
        Page.of([], 1, 3, 4).totalPages() == 2
    }

    def "totalElements=0ならtotalPages=0"() {
        expect:
        Page.of([], 1, 12, 0).totalPages() == 0
    }
}
