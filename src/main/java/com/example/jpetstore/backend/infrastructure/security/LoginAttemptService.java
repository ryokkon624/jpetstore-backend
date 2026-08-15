package com.example.jpetstore.backend.infrastructure.security;

import com.example.jpetstore.backend.infrastructure.audit.ProgramContext;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.LoginAttemptCustomMapper;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

/**
 * レート制限/ロックアウトの前段ゲート（#20 AC1・SBD-6・Draft 1）。
 *
 * <p>{@code AuthApplicationService.login} から呼ばれる想定。username を PK とする専用表（{@code t_login_attempt}）で
 * 連続失敗回数とロック期限を管理し、実在/非実在 username を対称に扱うことで列挙耐性を保つ（Draft 2＝{@code m_signon}
 * にロック列を持たせる案より列挙耐性が高いため採用。詳細は Sprint 4 実装方針参照）。
 *
 * <p>{@code isAccountNonLocked()}（{@code AuthenticatedUserDetails}）は意図的に触らない。Spring Security
 * 標準のロック機構は実在ユーザにしか 発火せずタイミング差による列挙の余地を生むため、本ゲートは authenticate() の外側（前段）で完結させる。
 */
@Component
public class LoginAttemptService {

  private static final String FALLBACK_PROGRAM = "SYSTEM";

  private final LoginAttemptCustomMapper mapper;
  private final LoginAttemptProperties properties;

  public LoginAttemptService(LoginAttemptCustomMapper mapper, LoginAttemptProperties properties) {
    this.mapper = mapper;
    this.properties = properties;
  }

  /**
   * username がロック中なら、既存の誤資格と同一の {@link BadCredentialsException} を投げて authenticate()
   * の前で短絡する（列挙不可・SBD-6）。
   *
   * <p>ロック判定は DB 側の {@code NOW(6)} で行う（{@link LoginAttemptCustomMapper#countActiveLock} 参照。app/DB
   * 間のクロックスキューに対して安全）。
   */
  public void assertNotLocked(String username) {
    if (mapper.countActiveLock(username) > 0) {
      throw new BadCredentialsException("Account is temporarily locked");
    }
  }

  /** ログイン失敗を記録する（単文アトミックUPDATE。並行リクエストでの取りこぼしを防ぐ）。 */
  public void recordFailure(String username) {
    mapper.recordFailure(
        username,
        properties.maxAttempts(),
        properties.lockDuration().toSeconds(),
        currentProgram());
  }

  /** ログイン成功時にカウンタ/ロックをリセットする。 */
  public void recordSuccess(String username) {
    mapper.recordSuccess(username);
  }

  private String currentProgram() {
    String program = ProgramContext.get();
    return (program == null || program.isBlank()) ? FALLBACK_PROGRAM : program;
  }
}
