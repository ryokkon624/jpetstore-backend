package com.example.jpetstore.backend.domain.exception;

/**
 * 注文確定時の在庫引当失敗（#8 AC2・計画フェーズ確定①）。
 *
 * <p>在庫ガード付きアトミック減算（{@code UPDATE t_inventory SET quantity = quantity - :n WHERE item_id = :id AND
 * quantity >= :n}）の affected rows が 0 のとき（在庫不足・同時発注の競合負けの両方を含む）、 {@code OrderApplicationService} が
 * {@code AffectedRows.requireUpdated(rows, supplier)} 経由でこの例外を投げる。 {@code GlobalExceptionHandler} が
 * 409 Conflict にマッピングする（既存の {@code OptimisticLockConflictException}=409 と系を揃える・計画フェーズ確定①）。
 *
 * <p>空カートでの確定要求も同系の失敗（注文として成立しない）として本例外を再利用する（{@link #itemId()} が {@code null}
 * の場合は「対象アイテム無し＝空カート」を表す。計画フェーズ確定②）。
 *
 * <p>メッセージは固定文言のみとし、内部詳細（itemId等）は含めない（backend-conventions §5・例外メッセージに 内部IDを含めない）。{@link #itemId()}
 * はログ・監査記録用の内部フィールドとして別途保持する。
 */
public class InsufficientStockException extends RuntimeException {

  private static final String DEFAULT_MESSAGE =
      "The order could not be placed because the requested quantity is no longer available."
          + " Please review your cart and try again.";

  private final String itemId;

  public InsufficientStockException(String itemId) {
    super(DEFAULT_MESSAGE);
    this.itemId = itemId;
  }

  /** 在庫不足の対象アイテムID。空カートによる失敗の場合は {@code null}。 */
  public String itemId() {
    return itemId;
  }
}
