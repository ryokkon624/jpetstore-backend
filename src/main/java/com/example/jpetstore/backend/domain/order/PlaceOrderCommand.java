package com.example.jpetstore.backend.domain.order;

/**
 * 注文確定ユースケースの入力コマンド（#8 AC1・計画フェーズ確定）。
 *
 * <p>数量・価格・username は一切持たない（SBD-2アローリスト。数量はDBカート、価格はサーバ再計算、
 * usernameは認証プリンシパルからそれぞれ導出するため、リクエストから受け取る必要が無い＝構造的にAC-neg1/ AC-neg3を担保する）。
 */
public record PlaceOrderCommand(
    OrderAddress billing, OrderAddress shipping, boolean useSeparateShipping) {

  /**
   * 実際の配送先を解決する。{@code useSeparateShipping=false} または {@code shipping} 未指定の場合は {@code billing}
   * をそのまま配送先として使う（依存の無い純粋な解決ロジックのため単体テスト可）。
   */
  public OrderAddress effectiveShipping() {
    return useSeparateShipping && shipping != null ? shipping : billing;
  }
}
