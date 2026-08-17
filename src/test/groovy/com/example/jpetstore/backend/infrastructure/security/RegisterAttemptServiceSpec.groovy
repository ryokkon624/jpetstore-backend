package com.example.jpetstore.backend.infrastructure.security

import com.example.jpetstore.backend.domain.exception.RegistrationRateLimitExceededException
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.RegisterAttemptCustomMapper
import spock.lang.Specification

import java.time.Duration

/**
 * #13 AC4/AC-neg2(SBD-6): 登録エンドポイントの総当り/列挙対策となるレート制限ゲート。
 *
 * <p>{@code LoginAttemptService}（username PK・失敗時のみ記録）とは異なり、登録は成功/失敗を問わず
 * 「1回の試行」自体がIPからの資源消費（アカウント大量作成の温床）であるため、{@code recordAttempt}は
 * 呼び出し元（{@code RegistrationApplicationService}）が結果を問わず毎回呼ぶ設計とする（詳細は
 * implementation-notes.md参照）。ロック判定はDB側のNOW(6)で行う（{@code
 * RegisterAttemptCustomMapper#countActiveLock}）ため、ここではcountActiveLockの戻り値に応じた分岐のみを検証する。
 */
class RegisterAttemptServiceSpec extends Specification {

    RegisterAttemptCustomMapper mapper = Mock()
    RegisterAttemptProperties properties = new RegisterAttemptProperties(5, Duration.ofMinutes(15))
    RegisterAttemptService service = new RegisterAttemptService(mapper, properties)

    def "レート制限中(countActiveLock>0)ならRegistrationRateLimitExceededExceptionを投げる"() {
        given:
        mapper.countActiveLock("203.0.113.1") >> 1

        when:
        service.assertNotRateLimited("203.0.113.1")

        then:
        thrown(RegistrationRateLimitExceededException)
    }

    def "レート制限されていない(countActiveLock==0)ならassertNotRateLimitedは通過する"() {
        given:
        mapper.countActiveLock("203.0.113.2") >> 0

        when:
        service.assertNotRateLimited("203.0.113.2")

        then:
        noExceptionThrown()
    }

    def "recordAttemptはmapperへclientIp/閾値/ロック期間(秒)/programを渡す"() {
        when:
        service.recordAttempt("203.0.113.3")

        then:
        1 * mapper.recordAttempt("203.0.113.3", 5, 900L, "SYSTEM")
    }
}
