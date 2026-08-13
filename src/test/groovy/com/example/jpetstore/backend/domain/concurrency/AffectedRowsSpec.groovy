package com.example.jpetstore.backend.domain.concurrency

import com.example.jpetstore.backend.domain.exception.OptimisticLockConflictException
import spock.lang.Specification

/**
 * AC8 (arch §4): affected-rows 判定の標準ヘルパ。
 * - 編集系（version楽観ロック）: 既定でOptimisticLockConflictExceptionを投げる。
 * - 在庫等のガード付き減算: 呼び出し側で任意の例外に差し替え可能。
 */
class AffectedRowsSpec extends Specification {

    def "1件以上更新されていれば何も起きない"() {
        when:
        AffectedRows.requireUpdated(1)

        then:
        noExceptionThrown()
    }

    def "0件だとOptimisticLockConflictExceptionを投げる(既定パターン=編集系version楽観ロック)"() {
        when:
        AffectedRows.requireUpdated(0)

        then:
        thrown(OptimisticLockConflictException)
    }

    def "0件のとき任意の例外Supplierに差し替えられる(在庫ガード付き減算等の別パターン用)"() {
        when:
        AffectedRows.requireUpdated(0, { new IllegalStateException("stock insufficient") })

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "stock insufficient"
    }

    def "任意Supplier指定時、1件以上なら例外は投げない"() {
        when:
        AffectedRows.requireUpdated(2, { new IllegalStateException("should not be thrown") })

        then:
        noExceptionThrown()
    }
}
