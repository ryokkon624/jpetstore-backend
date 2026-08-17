package com.example.jpetstore.backend.domain.cart;

import java.util.Optional;

/**
 * カート集約の永続化アクセスの唯一の入口（#29・{@code backend-conventions} §1/§2/§9）。
 *
 * <p>実装は Infrastructure 層（{@code
 * infrastructure.mybatis.cart.MyBatisCartRepository}）に置く（依存性逆転）。Application 層（{@code
 * CartApplicationService}）はこのインターフェイスのみに依存し、MyBatis の {@code CartCustomMapper} や {@code
 * *CustomEntity} を直接扱わない。
 */
public interface CartRepository {

  /**
   * 呼び出し元ユーザのカートを取得する（無ければ確保する。ensure＋読取射影・空カートも確保）。
   *
   * @param userId カートの持ち主（常に呼び出し元プリンシパル。IDOR面ゼロ）
   */
  Cart findByUserId(Long userId);

  /**
   * 追加/更新/マージの不変条件判定に必要な在庫コンテキストを取得する（{@code m_item} 起点）。
   *
   * @return {@code m_item} に該当行が無ければ {@code Optional.empty()}（アイテム不存在＝呼び出し元で404）。#28 でバッチ化（{@code
   *     findStocks(cartId, List<itemId>)}）へ拡張できる seam として、本メソッドは単一itemId解決のまま残す。
   */
  Optional<StockAvailability> findStock(Long cartId, String itemId);

  /** カート明細1行を最終的な絶対数量でupsertする（単一行・余分な書込ゼロ）。 */
  void upsertItem(Long cartId, CartItem line);

  /** カート明細1行を削除する（冪等。存在しない行への削除も成功する）。 */
  void removeItem(Long cartId, String itemId);
}
