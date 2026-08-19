package com.example.jpetstore.backend.domain.exception;

/**
 * ユーザー登録エンドポイントのレート制限超過（#13 AC4/AC-neg2・SBD-6）。
 *
 * <p>{@code RegisterAttemptService#acquireAttemptSlotOrThrow}（#41）が、クライアントIPの直近窓内試行数が閾値を
 * 超えている（{@code t_register_attempt.lock_until}が有効）と判定した場合に投げる。{@code GlobalExceptionHandler}が429 Too
 * Many Requestsに正規化する。
 */
public class RegistrationRateLimitExceededException extends RuntimeException {

  private static final String DEFAULT_MESSAGE =
      "Too many registration attempts. Please try again later.";

  public RegistrationRateLimitExceededException() {
    super(DEFAULT_MESSAGE);
  }
}
