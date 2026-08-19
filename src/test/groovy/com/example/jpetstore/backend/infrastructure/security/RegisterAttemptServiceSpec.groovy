package com.example.jpetstore.backend.infrastructure.security

import com.example.jpetstore.backend.domain.exception.RegistrationRateLimitExceededException
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.RegisterAttemptCustomMapper
import spock.lang.Specification

import java.time.Duration

/**
 * #13 AC4/AC-neg2(SBD-6)／#41 AC2/AC4(N4): 登録エンドポイントの総当り/列挙対策となるレート制限ゲート。
 *
 * <p>{@link RegisterAttemptService#acquireAttemptSlotOrThrow} は登録処理の前にスロットを原子的に確保する
 * （#41でcheck-then-actのTOCTOUを解消。旧{@code assertNotRateLimited}/{@code recordAttempt}の2段構えを統合）。
 * ロック判定はDB側のNOW(6)で行う（{@code RegisterAttemptCustomMapper#acquireSlot}のWHERE句）ため、ここでは
 * mapper の戻り値（affected rows）に応じた分岐のみを検証する。
 */
class RegisterAttemptServiceSpec extends Specification {

    RegisterAttemptCustomMapper mapper = Mock()
    RegisterAttemptProperties properties = new RegisterAttemptProperties(5, Duration.ofMinutes(15))
    RegisterAttemptService service = new RegisterAttemptService(mapper, properties)

    def "枠確保に失敗(acquireSlotのaffected rows==0)ならRegistrationRateLimitExceededExceptionを投げる"() {
        given:
        mapper.acquireSlot("203.0.113.1", 5, 900L, "SYSTEM") >> 0

        when:
        service.acquireAttemptSlotOrThrow("203.0.113.1")

        then:
        thrown(RegistrationRateLimitExceededException)
        1 * mapper.ensureRow("203.0.113.1", "SYSTEM")
    }

    def "枠確保に成功(acquireSlotのaffected rows==1)ならacquireAttemptSlotOrThrowは通過する"() {
        given:
        mapper.acquireSlot("203.0.113.2", 5, 900L, "SYSTEM") >> 1

        when:
        service.acquireAttemptSlotOrThrow("203.0.113.2")

        then:
        noExceptionThrown()
        1 * mapper.ensureRow("203.0.113.2", "SYSTEM")
    }

    def "acquireAttemptSlotOrThrowはensureRow→acquireSlotの順にclientIp/閾値/ロック期間(秒)/programを渡す"() {
        when:
        service.acquireAttemptSlotOrThrow("203.0.113.3")

        then:
        1 * mapper.ensureRow("203.0.113.3", "SYSTEM")

        then:
        1 * mapper.acquireSlot("203.0.113.3", 5, 900L, "SYSTEM") >> 1
    }
}
