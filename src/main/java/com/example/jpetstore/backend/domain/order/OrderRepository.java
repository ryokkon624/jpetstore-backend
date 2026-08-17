package com.example.jpetstore.backend.domain.order;

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
}
