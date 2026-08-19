package com.example.jpetstore.backend.infrastructure.security;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 未認証由来の監査（{@code t_audit_log}）write抑止（#39 AC3・N14・SBD-14）の窓・上限を保持する設定クラス。
 *
 * <p>秘密情報ではないためデフォルト値を持つ（未設定でも起動可能）。{@code application.yml} の {@code audit.write-quota.*}
 * で上書きできる（env経由の上書きも可）。
 *
 * <p>既定値（100回/1分）は既存の {@code auth.lockout}（5回/15分）を流用しない: あちらは「資格情報推測の予算」を絞る目的で
 * 意図的に小さいが、監査writeに同じ閾値を転用すると通常の401トラフィック（誤った直リンク・切れたブックマーク等）まで
 * 早期に黙らせてしまい、AC3が防ごうとしている「監査の完全性毀損」を別の形で再導入しかねない。
 */
@Component
public class AuditWriteQuotaProperties {

  private final int maxWrites;
  private final Duration window;

  public AuditWriteQuotaProperties(
      @Value("${audit.write-quota.max-writes:100}") int maxWrites,
      @Value("${audit.write-quota.window:PT1M}") Duration window) {
    this.maxWrites = maxWrites;
    this.window = window;
  }

  public int maxWrites() {
    return maxWrites;
  }

  public Duration window() {
    return window;
  }
}
