package com.example.jpetstore.backend.domain.exception;

/**
 * 楽観ロック競合（{@code version} 不一致＝affected rows == 0）を表す（AC8 → HTTP 409 に正規化マッピング）。
 *
 * <p>編集系エンティティの更新で {@code UPDATE ... WHERE pk=:id AND version=:readVersion} の affected rows が 0
 * のとき、呼び出し側（{@code AffectedRows.requireUpdated}）がこの例外を投げる （architecture-conventions §4.2）。
 */
public class OptimisticLockConflictException extends RuntimeException {

  private static final String DEFAULT_MESSAGE =
      "The resource was updated by another request. Please reload and try again.";

  public OptimisticLockConflictException() {
    super(DEFAULT_MESSAGE);
  }

  public OptimisticLockConflictException(String message) {
    super(message);
  }
}
