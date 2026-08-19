package com.example.jpetstore.backend.infrastructure.security;

import com.example.jpetstore.backend.domain.exception.RegistrationRateLimitExceededException;
import com.example.jpetstore.backend.infrastructure.audit.ProgramContext;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.RegisterAttemptCustomMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登録エンドポイントのレート制限ゲート（#13 AC4/AC-neg2・#41 AC2/AC4・SBD-6）。
 *
 * <p>{@code RegistrationApplicationService.register} から呼ばれる想定。client_ip を PK とする専用表（{@code
 * t_register_attempt}）で直近窓内の試行回数とロック期限を管理する（{@code LoginAttemptService}と同方式）。
 *
 * <p>{@code LoginAttemptService}との違い: ログインは「失敗」のみをカウントし成功時はリセットするが（誤資格の総当りを
 * 抑止する目的のため）、登録は成功/失敗を問わず「1回の試行」自体がIP由来の資源消費（アカウント大量作成の温床）で あるため、{@link #acquireAttemptSlotOrThrow}
 * は登録処理の**前**にスロットを確保するだけで、成功時のリセット（{@code recordSuccess}相当）は持たない（1回消費したら消費したまま。詳細は {@code
 * backlog/sprint_16/implementation-notes.md} 参照）。
 *
 * <p>#41: 旧 {@code assertNotRateLimited}（SELECT）/{@code recordAttempt}（呼び出し元の {@code finally} で毎回呼ぶ
 * UPDATE）の check-then-act 構造を {@link #acquireAttemptSlotOrThrow} 1本へ統合した（{@code
 * LoginAttemptService#acquireAttemptSlotOrThrow} と同じ設計判断）。
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
   * 登録処理の前にスロットを原子的に確保する（#41 AC2）。枠確保に失敗（ロック中）した場合は {@link RegistrationRateLimitExceededException}
   * を投げて登録処理へ進めずに短絡する。
   *
   * <p>{@link Propagation#REQUIRES_NEW}（F6）: 呼び出し元（{@code
   * RegistrationApplicationService#register}）は
   * username重複等で主{@code @Transactional}をロールバックすることがあるが、枠確保を主txに参加させると
   * ロールバックで消費した枠が巻き戻り、攻撃者が意図的に409（username重複）を誘発し続けることでレート制限を
   * バイパスできてしまう。枠確保自体は業務データの登録成否とは独立して常にコミットされる必要があるため、 {@code
   * AuditLogRecorder#recordStateChangeIndependently}と同じ設計判断で REQUIRES_NEW とする。呼び出し元は
   * 本メソッドを持つ本クラス（別bean）を経由して呼ぶため、Spring AOPプロキシが正しく介在する（自己呼び出しではない）。
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void acquireAttemptSlotOrThrow(String clientIp) {
    mapper.ensureRow(clientIp, currentProgram());
    int affected =
        mapper.acquireSlot(
            clientIp,
            properties.maxAttempts(),
            properties.lockDuration().toSeconds(),
            currentProgram());
    if (affected == 0) {
      throw new RegistrationRateLimitExceededException();
    }
  }

  private String currentProgram() {
    String program = ProgramContext.get();
    return (program == null || program.isBlank()) ? FALLBACK_PROGRAM : program;
  }
}
