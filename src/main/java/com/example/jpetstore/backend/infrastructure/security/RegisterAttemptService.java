package com.example.jpetstore.backend.infrastructure.security;

import com.example.jpetstore.backend.domain.exception.RegistrationRateLimitExceededException;
import com.example.jpetstore.backend.infrastructure.audit.ProgramContext;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.RegisterAttemptCustomMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登録エンドポイントのレート制限ゲート（#13 AC4/AC-neg2・SBD-6）。
 *
 * <p>{@code RegistrationApplicationService.register} から呼ばれる想定。client_ip を PK とする専用表（{@code
 * t_register_attempt}）で直近窓内の試行回数とロック期限を管理する（{@code LoginAttemptService}と同方式）。
 *
 * <p>{@code LoginAttemptService}との違い: ログインは「失敗」のみをカウントし成功時はリセットするが（誤資格の総当りを
 * 抑止する目的のため）、登録は成功/失敗を問わず「1回の試行」自体がIP由来の資源消費（アカウント大量作成の温床）で あるため、{@link #recordAttempt}
 * は呼び出し元が結果によらず毎回呼ぶ設計とする（recordSuccess相当のリセットは 持たない）。詳細は {@code
 * backlog/sprint_16/implementation-notes.md} 参照。
 *
 * <p>client_ip は呼び出し元（{@code RegistrationApplicationService}）が {@code request.getRemoteAddr()}
 * から解決する（X-Forwarded-Forは信頼しない・Sprint2教訓）。
 */
@Component
public class RegisterAttemptService {

  private static final String FALLBACK_PROGRAM = "SYSTEM";

  private final RegisterAttemptCustomMapper mapper;
  private final RegisterAttemptProperties properties;

  public RegisterAttemptService(
      RegisterAttemptCustomMapper mapper, RegisterAttemptProperties properties) {
    this.mapper = mapper;
    this.properties = properties;
  }

  /**
   * clientIp がレート制限中なら {@link RegistrationRateLimitExceededException} を投げる。
   *
   * <p>ロック判定は DB 側の {@code NOW(6)} で行う（{@link RegisterAttemptCustomMapper#countActiveLock}
   * 参照。app/DB 間のクロックスキューに対して安全）。
   */
  public void assertNotRateLimited(String clientIp) {
    if (mapper.countActiveLock(clientIp) > 0) {
      throw new RegistrationRateLimitExceededException();
    }
  }

  /**
   * 登録試行を記録する（単文アトミックUPDATE。並行リクエストでの取りこぼしを防ぐ）。成功/失敗を問わず毎回呼ぶ。
   *
   * <p>{@link Propagation#REQUIRES_NEW}: 呼び出し元（{@code RegistrationApplicationService#register}）は
   * username重複等でメインの{@code @Transactional}をロールバックすることがあるが、レート制限カウンタの
   * 更新は「試行があった」という事実の記録であり、業務データの登録成否とは独立して常にコミットされる必要が ある（{@code
   * AuditLogRecorder#recordStateChangeIndependently}と同じ設計判断）。呼び出し元は本メソッドを
   * 持つ本クラス（別bean）を経由して呼ぶため、Spring AOPプロキシが正しく介在する（自己呼び出しではない）。
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordAttempt(String clientIp) {
    mapper.recordAttempt(
        clientIp,
        properties.maxAttempts(),
        properties.lockDuration().toSeconds(),
        currentProgram());
  }

  private String currentProgram() {
    String program = ProgramContext.get();
    return (program == null || program.isBlank()) ? FALLBACK_PROGRAM : program;
  }
}
