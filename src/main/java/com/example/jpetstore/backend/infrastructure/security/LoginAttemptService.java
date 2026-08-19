package com.example.jpetstore.backend.infrastructure.security;

import com.example.jpetstore.backend.infrastructure.audit.ProgramContext;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.LoginAttemptCustomMapper;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

/**
 * レート制限/ロックアウトの前段ゲート（#20 AC1・#41 AC1/AC3/AC4・SBD-6）。
 *
 * <p>{@code AuthApplicationService.login} から呼ばれる想定。username を PK とする専用表（{@code t_login_attempt}）で
 * 連続失敗回数とロック期限を管理し、実在/非実在 username を対称に扱うことで列挙耐性を保つ（Draft 2＝{@code m_signon}
 * にロック列を持たせる案より列挙耐性が高いため採用。詳細は Sprint 4 実装方針参照）。
 *
 * <p>{@code isAccountNonLocked()}（{@code AuthenticatedUserDetails}）は意図的に触らない。Spring Security
 * 標準のロック機構は実在ユーザにしか 発火せずタイミング差による列挙の余地を生むため、本ゲートは authenticate() の外側（前段）で完結させる。
 *
 * <p>#41: 旧 {@code assertNotLocked}（SELECT）/{@code recordFailure}（UPDATE）の check-then-act 構造
 * （判定してから照合し、後で数える）を {@link #acquireAttemptSlotOrThrow} 1本へ統合した。詳細は同メソッドの javadoc 参照。
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
   * 資格情報照合（bcrypt）の前にスロットを原子的に確保する（#41 AC1）。
   *
   * <p>旧設計は「ロック判定（SELECT）→ authenticate()（bcrypt・数十〜百ms）→ 失敗計数（UPDATE）」の check-then-act
   * 構造だったため、並行バーストでは全リクエストがロック判定を通過してから authenticate()
   * に到達しうる穴があった（TOCTOU）。本メソッドは「照合前にスロットを確保する」単一の原子的 UPDATE （{@link
   * LoginAttemptCustomMapper#acquireSlot}）に置き換え、InnoDB の行ロックにより同一 username への 確保が直列化されるため、並行 N 本でも
   * {@code authenticate()} 到達回数が閾値（{@code maxAttempts}）近傍で 頭打ちになる。枠確保に失敗（ロック中）した場合は、既存の誤資格と同一の
   * {@link BadCredentialsException} を投げて {@code authenticate()} の前で短絡する（AC3・列挙不可。bcrypt を実行しないため現行の
   * {@code assertNotLocked} 短絡と同一のタイミング特性を保つ）。
   *
   * <p><b>受容したトレードオフ（S1）</b>: 本ゲートは「照合前」にスロットを消費するため、成功ログインも 枠を消費する（失敗だけを数えていた旧設計との違い）。bcrypt
   * 照合を行ロック内に抱えない（username単位で 全ログインが直列化する新たな DoS 面を作らないための設計判断。{@link #recordSuccess} の項参照）代償として、
   * 同一 username への max-attempts+1 本以上の「同時成功」ログインでは最後の1本が誤401になり得る。ただし {@link #recordSuccess} の
   * DELETE により次リクエストで即座に自己回復するため実害は極小である。
   */
  public void acquireAttemptSlotOrThrow(String username) {
    mapper.ensureRow(username, currentProgram());
    int affected =
        mapper.acquireSlot(
            username,
            properties.maxAttempts(),
            properties.lockDuration().toSeconds(),
            currentProgram());
    if (affected == 0) {
      throw new BadCredentialsException("Account is temporarily locked");
    }
  }

  /**
   * ログイン成功時にカウンタ/ロックをリセットする。
   *
   * <p>{@code login} に {@code @Transactional} を付けない（bcrypt を行ロック内に抱えると username 単位で 全ログインが直列化し新たな
   * DoS 面になるため）代償として、{@link #acquireAttemptSlotOrThrow} は成功 ログインも枠を消費する。本メソッドの DELETE
   * がその消費分を即座に払拭し、次回リクエスト以降は新規窓として 扱われる（S1のトレードオフの自己回復機構）。
   */
  public void recordSuccess(String username) {
    mapper.recordSuccess(username);
  }

  private String currentProgram() {
    String program = ProgramContext.get();
    return (program == null || program.isBlank()) ? FALLBACK_PROGRAM : program;
  }
}
