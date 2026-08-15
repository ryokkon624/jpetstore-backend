package com.example.jpetstore.backend.domain.catalog;

import com.example.jpetstore.backend.domain.enums.StockStatus;
import java.math.BigDecimal;

/**
 * 商品内の在庫アイテム一覧1件を表す参照系ドメインモデル（#1）。
 *
 * <p>{@link #stockStatus} は {@code ItemSummaryCustomEntity#quantity} を {@code
 * StockStatusCalculator#of}
 * で変換済みの値であり、qty自体はこのモデルより上位（Application/Presentation）へは一切渡さない（AC3・在庫数非露出）。
 */
public record ItemSummary(
    String itemId,
    String productId,
    String attribute1,
    BigDecimal listPrice,
    StockStatus stockStatus) {}
