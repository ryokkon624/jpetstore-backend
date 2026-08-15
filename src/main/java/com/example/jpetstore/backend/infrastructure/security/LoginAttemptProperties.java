package com.example.jpetstore.backend.infrastructure.security;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * レート制限/ロックアウト（#20 AC1・SBD-6）の閾値・ロック期間を保持する設定クラス。
 *
 * <p>秘密情報ではないため {@link com.example.jpetstore.backend.infrastructure.security.JwtProperties}
 * と異なりデフォルト値を持つ（未設定でも起動可能）。{@code application.yml} の {@code auth.lockout.*} で上書きできる（env 経由の上書きも可）。
 */
@Component
public class LoginAttemptProperties {

  private final int maxAttempts;
  private final Duration lockDuration;

  public LoginAttemptProperties(
      @Value("${auth.lockout.max-attempts:5}") int maxAttempts,
      @Value("${auth.lockout.lock-duration:PT15M}") Duration lockDuration) {
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
