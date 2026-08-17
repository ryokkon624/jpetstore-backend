package com.example.jpetstore.backend.domain.exception;

/**
 * ユーザー登録時に指定された {@code username} が既存行と重複していることを表す（#13 E4・SBD-6）。
 *
 * <p>{@code m_account.uk_m_account_username} のUNIQUE制約違反（{@code
 * org.springframework.dao.DuplicateKeyException}）を {@code RegistrationApplicationService} が捕捉して
 * この例外に変換する。{@code GlobalExceptionHandler}が409 Conflict＋明示メッセージに正規化する（列挙対策は レート制限（{@code
 * RegisterAttemptService}）が担保する前提・E4計画確定）。
 */
public class UsernameAlreadyExistsException extends RuntimeException {

  private static final String DEFAULT_MESSAGE = "Username is already in use.";

  public UsernameAlreadyExistsException() {
    super(DEFAULT_MESSAGE);
  }
}
