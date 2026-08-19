package com.example.jpetstore.backend.infrastructure.security

import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.LoginAttemptCustomMapper
import org.springframework.security.authentication.BadCredentialsException
import spock.lang.Specification

import java.time.Duration

/**
 * #20 AC1(SBD-6)／#41 AC1/AC4(N4) レート制限/ロックアウトの前段ゲート。
 *
 * <p>{@code AuthApplicationService.login} から呼ばれる想定。{@link LoginAttemptService#acquireAttemptSlotOrThrow}
 * は照合前にスロットを原子的に確保する（#41でcheck-then-actのTOCTOUを解消）。枠確保に失敗（mapperの
 * {@code acquireSlot}のaffected rows==0＝ロック中）した場合は既存の誤資格と同一の {@code
 * BadCredentialsException} を投げ（列挙不可・SBD-6）、authenticate() を呼ばずに短絡する。ロック判定自体は
 * DB側のNOW(6)で行う（{@code LoginAttemptCustomMapper#acquireSlot}のWHERE句）ため、ここでは mapper の
 * 戻り値（affected rows）に応じた分岐のみを検証する。
 */
class LoginAttemptServiceSpec extends Specification {

    LoginAttemptCustomMapper mapper = Mock()
    LoginAttemptProperties properties = new LoginAttemptProperties(5, Duration.ofMinutes(15))
    LoginAttemptService service = new LoginAttemptService(mapper, properties)

    def "枠確保に失敗(acquireSlotのaffected rows==0)ならBadCredentialsExceptionを投げる(既存の誤資格と同一の例外型・列挙不可)"() {
        given:
        mapper.acquireSlot("locked_user", 5, 900L, "SYSTEM") >> 0

        when:
        service.acquireAttemptSlotOrThrow("locked_user")

        then:
        thrown(BadCredentialsException)
        1 * mapper.ensureRow("locked_user", "SYSTEM")
    }

    def "枠確保に成功(acquireSlotのaffected rows==1)ならacquireAttemptSlotOrThrowは通過する"() {
        given:
        mapper.acquireSlot("ok_user", 5, 900L, "SYSTEM") >> 1

        when:
        service.acquireAttemptSlotOrThrow("ok_user")

        then:
        noExceptionThrown()
        1 * mapper.ensureRow("ok_user", "SYSTEM")
    }

    def "acquireAttemptSlotOrThrowはensureRow→acquireSlotの順にusername/閾値/ロック期間(秒)/programを渡す"() {
        when:
        service.acquireAttemptSlotOrThrow("someone")

        then:
        1 * mapper.ensureRow("someone", "SYSTEM")

        then:
        1 * mapper.acquireSlot("someone", 5, 900L, "SYSTEM") >> 1
    }

    def "recordSuccessはmapperのカウンタリセットを呼ぶ"() {
        when:
        service.recordSuccess("someone")

        then:
        1 * mapper.recordSuccess("someone")
    }
}
