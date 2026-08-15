package com.example.jpetstore.backend.infrastructure.security

import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.LoginAttemptCustomMapper
import org.springframework.security.authentication.BadCredentialsException
import spock.lang.Specification

import java.time.Duration

/**
 * #20 AC1(SBD-6) レート制限/ロックアウトの前段ゲート（Draft 1）。
 *
 * <p>{@code AuthApplicationService.login} から呼ばれる想定。{@code assertNotLocked} はロック中なら既存の誤資格と同一の {@code
 * BadCredentialsException} を投げ（列挙不可・SBD-6）、authenticate() を呼ばずに短絡する。ロック判定自体は DB 側の {@code
 * NOW(6)} で行う（{@code LoginAttemptCustomMapper#countActiveLock}）ため、ここでは countActiveLock の戻り値に応じた
 * 分岐のみを検証する（クロックスキュー環境でJava側の時計比較が機能しない不具合がIT実装時に見つかったための設計）。
 */
class LoginAttemptServiceSpec extends Specification {

    LoginAttemptCustomMapper mapper = Mock()
    LoginAttemptProperties properties = new LoginAttemptProperties(5, Duration.ofMinutes(15))
    LoginAttemptService service = new LoginAttemptService(mapper, properties)

    def "ロック中(countActiveLock>0)ならBadCredentialsExceptionを投げる(既存の誤資格と同一の例外型・列挙不可)"() {
        given:
        mapper.countActiveLock("locked_user") >> 1

        when:
        service.assertNotLocked("locked_user")

        then:
        thrown(BadCredentialsException)
    }

    def "ロックされていない(countActiveLock==0)ならassertNotLockedは通過する"() {
        given:
        mapper.countActiveLock("ok_user") >> 0

        when:
        service.assertNotLocked("ok_user")

        then:
        noExceptionThrown()
    }

    def "recordFailureはmapperへusername/閾値/ロック期間(秒)/programを渡す"() {
        when:
        service.recordFailure("someone")

        then:
        1 * mapper.recordFailure("someone", 5, 900L, "SYSTEM")
    }

    def "recordSuccessはmapperのカウンタリセットを呼ぶ"() {
        when:
        service.recordSuccess("someone")

        then:
        1 * mapper.recordSuccess("someone")
    }
}
