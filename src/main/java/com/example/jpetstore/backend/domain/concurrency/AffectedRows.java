package com.example.jpetstore.backend.domain.concurrency;

import com.example.jpetstore.backend.domain.exception.OptimisticLockConflictException;
import java.util.function.Supplier;

/**
 * affected-rows 判定の標準ヘルパ（AC8・arch §4）。
 *
 * <p>並行制御の2パターンに対応する：
 *
 * <ul>
 *   <li>編集系＝{@code version} 楽観ロック（{@code UPDATE ... WHERE pk=:id AND version=:readVersion}） →
 *       affected rows == 0 は競合。既定で {@link OptimisticLockConflictException}（→ 409）。
 *   <li>在庫等のガード付きアトミック減算（{@code UPDATE ... WHERE qty >= :n}）→ affected rows == 0 は在庫不足等、409
 *       とは異なる意味。呼び出し側が {@link Supplier} で任意の例外に差し替える。
 * </ul>
 */
public final class AffectedRows {

  private AffectedRows() {}

  /** affected rows が 0 なら {@link OptimisticLockConflictException} を投げる（編集系の既定パターン）。 */
  public static void requireUpdated(int rows) {
    requireUpdated(rows, OptimisticLockConflictException::new);
  }

  /** affected rows が 0 なら {@code exceptionSupplier} が生成した例外を投げる（在庫ガード付き減算等）。 */
  public static void requireUpdated(
      int rows, Supplier<? extends RuntimeException> exceptionSupplier) {
    if (rows == 0) {
      throw exceptionSupplier.get();
    }
  }
}
