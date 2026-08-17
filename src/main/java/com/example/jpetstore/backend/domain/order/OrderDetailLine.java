package com.example.jpetstore.backend.domain.order;

import java.math.BigDecimal;

/**
 * 注文詳細（#10 AC2）の明細1行を表す読取専用ドメインモデル。
 *
 * <p>{@link #productName} は {@code t_order_line ⋈ m_item ⋈ m_product} のJOINで補完する（ID-24・非等価改善。
 * as-isは履歴経由で明細の商品名が空だった）。{@link #unitPrice} は注文確定時点の単価（{@code
 * t_order_line.unit_price}・BigDecimal・SBD-13）。
 */
public record OrderDetailLine(
    String itemId, String productName, int quantity, BigDecimal unitPrice) {}
