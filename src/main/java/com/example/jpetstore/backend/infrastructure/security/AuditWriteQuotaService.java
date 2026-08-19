package com.example.jpetstore.backend.infrastructure.security;

import com.example.jpetstore.backend.infrastructure.audit.ProgramContext;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.AuditWriteQuotaCustomMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 未認証由来の監査（{@code t_audit_log}）write抑止ゲート（#39 AC3・N14）。
 *
 * <p>{@code AuditLogRecorder#recordAuthzFailure} が未認証（actor==null）のときのみ呼ぶ想定。認証済みの403には 掛けない:
 * 認証済みリクエストにも quota を掛けると、攻撃者が自分の枠を意図的に使い切ることで以降の自身の認可失敗を 無記録化でき、#39
 * が直している監査抑止（N2）をそのまま再導入してしまうため（未認証限定はユーザー承認済みの設計判断。 {@code backlog/sprint_20/sprint_backlog.md}
 * Q1参照）。
 *
 * <p>client_ip を PK とする専用表（{@code t_audit_write_quota}）で固定窓（既定1分）内の書き込み回数を管理する（{@code
 * RegisterAttemptService}と同方式・別テーブル）。
 */
@Component
public class AuditWriteQuotaService {

  private static final Logger log = LoggerFactory.getLogger(AuditWriteQuotaService.class);
  private static final String FALLBACK_PROGRAM = "SYSTEM";

  private final AuditWriteQuotaCustomMapper mapper;
  private final AuditWriteQuotaProperties properties;

  public AuditWriteQuotaService(
      AuditWriteQuotaCustomMapper mapper, AuditWriteQuotaProperties properties) {
    this.mapper = mapper;
    this.properties = properties;
  }

  /**
   * {@code clientIp} の窓内書き込み枠を1つ確保できたら {@code true}。上限超過なら {@code false} を返し、抑止の事実を {@code
   * suppressed_count} とアプリログ（WARN）へ残す（黙って消さない）。
   *
   * <p>{@link Propagation#REQUIRES_NEW}: 監査記録の可否を決めるセキュリティ制御であり、呼び出し元（{@code
   * AuditLogRecorder}）の状態に関わらず常に確定的にコミットされる必要がある（{@code
   * RegisterAttemptService#recordAttempt}と同じ設計判断）。呼び出し元は本メソッドを持つ本クラス（別bean）を経由して 呼ぶため、Spring
   * AOPプロキシが正しく介在する（自己呼び出しではない）。
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean tryAcquire(String clientIp) {
    String program = currentProgram();
    mapper.ensureRow(clientIp, program);
    int affected =
        mapper.tryAcquire(
            clientIp, properties.maxWrites(), properties.window().toSeconds(), program);
    if (affected == 0) {
      mapper.recordSuppressed(clientIp, program);
      log.warn(
          "Unauthenticated audit write suppressed for clientIp={} (quota exceeded: {} writes / {})",
          clientIp,
          properties.maxWrites(),
          properties.window());
      return false;
    }
    return true;
  }

  private String currentProgram() {
    String program = ProgramContext.get();
    return (program == null || program.isBlank()) ? FALLBACK_PROGRAM : program;
  }
}
