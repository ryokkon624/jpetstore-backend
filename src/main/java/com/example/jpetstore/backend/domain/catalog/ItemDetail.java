package com.example.jpetstore.backend.domain.catalog;

import com.example.jpetstore.backend.domain.enums.StockStatus;
import java.math.BigDecimal;

/**
 * アイテム詳細を表す参照系ドメインモデル（#1）。商品名/商品説明をJOINで併せ持つ（legacy {@code item.getProduct()} 相当）。
 *
 * <p>{@link #stockStatus} は {@code ItemDetailCustomEntity#quantity} を {@code
 * StockStatusCalculator#of}
 * で変換済みの値であり、qty自体はこのモデルより上位（Application/Presentation）へは一切渡さない（AC3・在庫数非露出）。
 */
public record ItemDetail(
    String itemId,
    String productId,
    String productName,
    String productDescription,
    String attribute1,
    BigDecimal listPrice,
    StockStatus stockStatus) {}
