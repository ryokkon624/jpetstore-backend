package com.example.jpetstore.backend.infrastructure.security;

import com.example.jpetstore.backend.infrastructure.audit.ProgramContext;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.LoginAttemptCustomMapper;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
   *
   * <p><b>perf是正（{@code @Transactional(REQUIRES_NEW)}）</b>: {@link
   * LoginAttemptCustomMapper#ensureRow} （INSERT..ODKU）と {@link
   * LoginAttemptCustomMapper#acquireSlot}（UPDATE）が個別に autocommit されコミットが2回発生していたため、 {@code
   * RegisterAttemptService#acquireAttemptSlotOrThrow}/{@code AuditWriteQuotaService#tryAcquire}
   * と同じく1トランザクションへまとめた。「bcrypt を行ロック内に抱えない」という設計意図は損なわれない: {@code AuthApplicationService.login}
   * は本メソッドの **return 後**に {@code authenticationManager.authenticate}
   * （bcrypt）を呼ぶため、本メソッドの行ロック・トランザクションは bcrypt 開始前に必ずコミット済みになる。呼び出し元（{@code
   * AuthApplicationService}）は本メソッドを持つ本クラス（別bean）を経由して呼ぶため、Spring AOPプロキシが正しく介在する（自己呼び出しではない）。
   *
   * <p><b>ロールバック安全性の不変条件（レート制限バイパス防止）</b>: 本メソッドが投げる {@link BadCredentialsException} は {@code
   * affected == 0}（＝枠を確保**できなかった**）ときにのみ送出される。 ロールバックされるのは「枠を消費していないケース」だけであり、枠確保に成功した後で例外を投げる経路は
   * 存在しない。**将来この不変条件を崩す変更（枠確保成功後に例外を投げる等）をしないこと**——崩すと、 枠消費がロールバックで巻き戻り、意図的な例外送出でレート制限を回避できてしまう。
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
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
