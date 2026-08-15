package com.example.jpetstore.backend.domain.common

import spock.lang.Specification

/** #1 論点3: page/size正規化(1-index・既定12・cap100)とoffset算出を検証する。 */
class PageRequestSpec extends Specification {

    def "page/size省略時は既定値(page=1, size=12)になる"() {
        given:
        def req = PageRequest.of(null, null)

        expect:
        req.page() == 1
        req.size() == 12
    }

    def "page<1やsize<1は1/既定値にクランプされる(400にしない)"() {
        given:
        def req = PageRequest.of(0, 0)

        expect:
        req.page() == 1
        req.size() == 12
    }

    def "sizeが上限(100)を超えるとcapされる"() {
        given:
        def req = PageRequest.of(1, 500)

        expect:
        req.size() == 100
    }

    def "offset()は1-indexのpageから0-index換算のoffsetを返す"() {
        expect:
        new PageRequest(1, 2).offset() == 0
        new PageRequest(2, 2).offset() == 2
        new PageRequest(3, 2).offset() == 4
    }
}
