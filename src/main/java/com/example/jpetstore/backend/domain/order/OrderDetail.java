package com.example.jpetstore.backend.domain.order;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 注文詳細閲覧（#10 AC2/AC4）の応答を表す読取専用ドメインモデル。
 *
 * <p>Sprint 14 Q2確定（AC どおり最小）: 明細（商品名・単価・数量）＋注文合計＋注文日のみを持つ。配送先/請求先住所は
 * 意図的に含めない（過剰実装回避。詳細画面には表示しない）。所有者（userId）も持たない（応答DTOへ所有者を漏らさない）。
 */
public record OrderDetail(
    Long orderId, LocalDate orderDate, BigDecimal totalPrice, List<OrderDetailLine> lines) {

  public OrderDetail {
    lines = List.copyOf(lines);
  }
}
