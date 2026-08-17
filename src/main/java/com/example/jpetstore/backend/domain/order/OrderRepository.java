package com.example.jpetstore.backend.domain.order;

import java.util.List;
import java.util.Optional;

/**
 * 注文（{@code t_order}/{@code t_order_line}）の永続化アクセスの唯一の入口（#30・{@code backend-conventions} §1/§2/§9）。
 *
 * <p>実装は Infrastructure 層（{@code infrastructure.mybatis.order.MyBatisOrderRepository}）に置く（依存性逆転）。
 * Application 層（{@code OrderApplicationService}）はこのインターフェイスのみに依存し、MyBatis の {@code
 * OrderCustomMapper} や {@code *CustomEntity} を直接扱わない。
 *
 * <p>O1（案A）: rich な Order 集約は作らず、各メソッドは単文アトミック委譲に純化する。#8 の並行オーケストレーション
 * （item_id昇順固定順ループ・{@code @Transactional}・全体のロールバック）は {@code OrderApplicationService} 側に残る。
 */
public interface OrderRepository {

  /**
   * 注文ヘッダを1件INSERTする。
   *
   * @return 生成された注文ID（{@code AUTO_INCREMENT}・ID-23）
   */
  Long insertHeader(NewOrder order);

  /** 注文明細を1件INSERTする。 */
  void insertLine(Long orderId, OrderLine line);

  /** #9 AC1: 本人の注文履歴一覧を新しい順（{@code order_id DESC}）でページング取得する。 */
  List<OrderSummary> listByUser(Long userId, int offset, int limit);

  /** #9 AC1: 本人の注文件数（ページング用total）。 */
  long countByUser(Long userId);

  /**
   * #10: 注文ヘッダを1件取得する（所有者解決＋応答の両方に使う）。存在しなければ{@link Optional#empty()}。
   *
   * <p>識別子解決用と最終応答用を分けず1回のSELECTで済ませる（Sprint12/13の教訓＝perf純増防止。{@code
   * OrderApplicationService#getOrder} は本メソッドの結果を所有者判定にも最終応答にも使い回す）。
   */
  Optional<OrderHeader> findHeaderById(Long orderId);

  /** #10 AC2: 注文明細をline_num順で取得する（商品名JOIN済）。認可通過後にのみ呼ぶ想定（perf設計）。 */
  List<OrderDetailLine> findLinesByOrderId(Long orderId);
}
