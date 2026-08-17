package com.example.jpetstore.backend.domain.cart;

import com.example.jpetstore.backend.domain.catalog.StockStatusCalculator;
import com.example.jpetstore.backend.domain.enums.StockStatus;
import java.math.BigDecimal;

/**
 * カート内アイテム1件を表す集約内エンティティ（#4・#29 D1=案A）。
 *
 * <p>{@link #quantity} はユーザ自身が入力した値であり、表示に必須のためレスポンスへ含めてよい（ID-28非該当）。一方、在庫数そのもの（qty）は {@link
 * #stockQuantity} として内部にのみ保持し、上位（Application/Presentation層）へは一切渡さない。外向きに公開するのは {@link #stockStatus}
 * と {@link #exceedsStock} の射影アクセサのみで、生の在庫qty getter は持たない（在庫数非露出＝ID-28を型で強制）。
 *
 * <p>{@link #lineTotal} は {@code listPrice × quantity}（サーバ計算・SBD-2/13）。{@link #exceedsStock}
 * は「既存カート行が後から在庫減で上限超過した」場合の表示バッジ警告用（cart.md §5・E3で実強制）。
 *
 * <p>{@link #reconstruct} は {@code CartConverter}（Repository実装内）からのDB行の再構築に使う。{@link #forWrite} は
 * {@link Cart} のコマンドメソッド（{@code addItem}/{@code updateItem}/{@code mergeLine}）が {@code
 * CartRepository#upsertItem} への書込み用の行を組み立てる内部専用ファクトリで、表示用フィールド（productName等）を持たない（呼び出し元は書込み後に
 * {@code CartRepository#findByUserId} を再取得して表示用の完全な {@link CartItem} を得る）。
 */
public final class CartItem {

  private final String itemId;
  private final String productId;
  private final String productName;
  private final String attribute1;
  private final int quantity;
  private final BigDecimal listPrice;
  private final int stockQuantity;

  private CartItem(
      String itemId,
      String productId,
      String productName,
      String attribute1,
      int quantity,
      BigDecimal listPrice,
      int stockQuantity) {
    this.itemId = itemId;
    this.productId = productId;
    this.productName = productName;
    this.attribute1 = attribute1;
    this.quantity = quantity;
    this.listPrice = listPrice;
    this.stockQuantity = stockQuantity;
  }

  /** DB行（{@code CartItemCustomEntity}）からの再構築（{@code CartConverter} からのみ呼ぶ）。 */
  public static CartItem reconstruct(
      String itemId,
      String productId,
      String productName,
      String attribute1,
      int quantity,
      BigDecimal listPrice,
      int stockQuantity) {
    return new CartItem(
        itemId, productId, productName, attribute1, quantity, listPrice, stockQuantity);
  }

  /**
   * {@code CartRepository#upsertItem} への書込み専用の行を組み立てる（{@link Cart} のコマンドメソッド内部専用）。表示用フィールドは持たない
   * （呼び出し元は書込み後に {@code findByUserId} で完全な {@link CartItem} を再取得する）。
   */
  static CartItem forWrite(String itemId, int quantity, int stockQuantity) {
    return new CartItem(itemId, null, null, null, quantity, null, stockQuantity);
  }

  public String itemId() {
    return itemId;
  }

  public String productId() {
    return productId;
  }

  public String productName() {
    return productName;
  }

  public String attribute1() {
    return attribute1;
  }

  public int quantity() {
    return quantity;
  }

  public BigDecimal listPrice() {
    return listPrice;
  }

  public BigDecimal lineTotal() {
    return listPrice.multiply(BigDecimal.valueOf(quantity));
  }

  public StockStatus stockStatus() {
    return StockStatusCalculator.of(stockQuantity);
  }

  public boolean exceedsStock() {
    return quantity > stockQuantity;
  }
}
