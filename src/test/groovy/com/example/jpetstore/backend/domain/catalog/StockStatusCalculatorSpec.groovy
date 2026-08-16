package com.example.jpetstore.backend.domain.catalog

import com.example.jpetstore.backend.domain.enums.StockStatus
import spock.lang.Specification

/**
 * #1 AC3・論点2（ユーザー承認 2026-08-16）: 在庫数(qty)からバッジ用のStockStatusを算出する。
 * 閾値 N=5 は非生成クラス側の定数として持つ（m_code区分値の生成enumには入れない＝再生成で消えるため）。
 * qty<=0=在庫切れ／0<qty<=5=残少／qty>5=在庫あり。
 */
class StockStatusCalculatorSpec extends Specification {

    def "of(#quantity) は #expected を返す(N=5)"() {
        expect:
        StockStatusCalculator.of(quantity) == expected

        where:
        quantity | expected
        -1       | StockStatus.OUT_OF_STOCK
        0        | StockStatus.OUT_OF_STOCK
        1        | StockStatus.LOW_STOCK
        3        | StockStatus.LOW_STOCK
        5        | StockStatus.LOW_STOCK
        6        | StockStatus.IN_STOCK
        100      | StockStatus.IN_STOCK
    }

    def "LOW_STOCK_THRESHOLDはマジックナンバーではなく定数として公開されている(N=5)"() {
        expect:
        StockStatusCalculator.LOW_STOCK_THRESHOLD == 5
    }
}
