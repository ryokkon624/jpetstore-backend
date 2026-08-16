package com.example.jpetstore.backend.domain.catalog;

/**
 * 指定数量でアイテムを注文可能かの判定結果（#4 D1・匿名でも数量検証できる公開EP用）。
 *
 * <p>{@link #orderable} が {@code false} の場合のみ {@link #reason} が安定した理由コードを持つ（{@code true} の場合は
 * {@code null}）。在庫数そのものは一切保持・露出しない（ID-28・在庫数非露出）。
 */
public record OrderabilityResult(boolean orderable, String reason) {

  public static final String REASON_OUT_OF_STOCK = "OUT_OF_STOCK";
  public static final String REASON_EXCEEDS_STOCK = "EXCEEDS_STOCK";
  public static final String REASON_INVALID_QUANTITY = "INVALID_QUANTITY";

  public static OrderabilityResult ok() {
    return new OrderabilityResult(true, null);
  }

  public static OrderabilityResult notOrderable(String reason) {
    return new OrderabilityResult(false, reason);
  }
}
