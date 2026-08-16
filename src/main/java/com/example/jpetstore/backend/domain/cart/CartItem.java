package com.example.jpetstore.backend.domain.cart;

import com.example.jpetstore.backend.domain.enums.StockStatus;
import java.math.BigDecimal;

/**
 * カート内アイテム1件を表すドメインモデル（#4）。
 *
 * <p>{@link #quantity} はユーザ自身が入力した値であり、表示に必須のためレスポンスへ含めてよい（ID-28非該当）。一方、在庫数そのもの（qty）はこのモデルより上位へ渡さない
 * （{@link #stockStatus} と {@link #exceedsStock} のみを露出する。在庫数非露出）。
 *
 * <p>{@link #lineTotal} は {@code listPrice × quantity}（サーバ計算・SBD-2/13）。{@link #exceedsStock}
 * は「既存カート行が後から在庫減で上限超過した」場合の表示バッジ警告用（cart.md §5・E3で実強制）。
 */
public record CartItem(
    String itemId,
    String productId,
    String productName,
    String attribute1,
    int quantity,
    BigDecimal listPrice,
    BigDecimal lineTotal,
    StockStatus stockStatus,
    boolean exceedsStock) {}
