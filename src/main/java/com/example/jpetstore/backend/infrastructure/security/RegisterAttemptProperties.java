package com.example.jpetstore.backend.infrastructure.security;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 登録エンドポイントのレート制限（#13 AC4・SBD-6）の閾値・ロック期間を保持する設定クラス。
 *
 * <p>秘密情報ではないため {@link com.example.jpetstore.backend.infrastructure.security.JwtProperties}
 * と異なりデフォルト値を持つ（未設定でも起動可能）。{@code application.yml} の {@code auth.register-rate-limit.*} で上書きできる（env
 * 経由の上書きも可）。既定値は {@link LoginAttemptProperties} と同じ（5回/15分）とする（DEV調整）。
 */
@Component
public class RegisterAttemptProperties {

  private final int maxAttempts;
  private final Duration lockDuration;

  public RegisterAttemptProperties(
      @Value("${auth.register-rate-limit.max-attempts:5}") int maxAttempts,
      @Value("${auth.register-rate-limit.lock-duration:PT15M}") Duration lockDuration) {
    this.maxAttempts = maxAttempts;
    this.lockDuration = lockDuration;
  }

  public int maxAttempts() {
    return maxAttempts;
  }

  public Duration lockDuration() {
    return lockDuration;
  }
}
