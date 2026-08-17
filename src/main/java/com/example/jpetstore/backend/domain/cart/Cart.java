package com.example.jpetstore.backend.domain.cart;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * カートを表す集約ルート（#4 AC1・#29 D1=案A）。
 *
 * <p>{@link #subtotal} はサーバ計算（{@code Σ(listPrice × quantity)}・[L2] 旧同値・SBD-2/13）で、{@link
 * CartItem#lineTotal} の合算を都度算出する。
 *
 * <p>在庫上限（在庫数）・加算オーバーフロー（{@link Math#addExact(int, int)}）・mergeクランプ・数量0以下の削除シグナルといった不変条件は、旧 {@code
 * CartApplicationService} の手続き的実装から本クラスのコマンドメソッド（{@link #addItem}/{@link #updateItem}/{@link
 * #mergeLine}）へ移設した（AC3）。在庫コンテキスト（{@link StockAvailability}）は集約内に保持せず、呼び出し元（{@code
 * CartApplicationService}）が {@code CartRepository#findStock}
 * で別途解決しコマンドメソッドへ注入する（D3・#28のバッチ化を妨げない構造）。
 */
public final class Cart {

  private final Long cartId;
  private final List<CartItem> items;

  private Cart(Long cartId, List<CartItem> items) {
    this.cartId = cartId;
    this.items = List.copyOf(items);
  }

  /** {@code CartConverter}（Repository実装内）からの再構築に使う。 */
  public static Cart reconstruct(Long cartId, List<CartItem> items) {
    return new Cart(cartId, items);
  }

  public Long cartId() {
    return cartId;
  }

  public List<CartItem> items() {
    return items;
  }

  public BigDecimal subtotal() {
    return items.stream().map(CartItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /**
   * カートへの追加（AC1・legacyの「既にあれば+1」を一般化した明示数量版）。既存数量に {@code requestedQuantity}
   * を加算した絶対値を上限チェックのうえ返す（永続化は呼び出し元が {@code CartRepository#upsertItem} で行う）。
   *
   * <p>sec指摘対応（SBD-2）: {@code requestedQuantity} は正の整数のみ受理する。既存数量との加算は {@link Math#addExact(int,
   * int)} でオーバーフロー検出する（intラップにより負の巨大な値が上限チェックを迂回し、負の数量が永続化されてしまう不具合を防ぐ）。
   *
   * @return 永続化すべき絶対数量を保持する書込み用の {@link CartItem}
   * @throws IllegalArgumentException
   *     数量が0以下、在庫切れ、加算後の数量が在庫数を超える、または加算がintをオーバーフローする場合（AC5/AC-neg1・SBD-2）
   */
  public CartItem addItem(StockAvailability stock, int requestedQuantity) {
    if (requestedQuantity <= 0) {
      throw new IllegalArgumentException("Requested quantity must be a positive integer");
    }
    if (stock.stockQuantity() <= 0) {
      throw new IllegalArgumentException("Item is out of stock");
    }
    int newQuantity;
    try {
      newQuantity = Math.addExact(stock.currentQuantity(), requestedQuantity);
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException("Requested quantity exceeds available stock");
    }
    if (newQuantity > stock.stockQuantity()) {
      throw new IllegalArgumentException("Requested quantity exceeds available stock");
    }
    return CartItem.forWrite(stock.itemId(), newQuantity, stock.stockQuantity());
  }

  /**
   * カート内数量の明示更新（AC1・{itemId, quantity} API）。数量0以下は削除シグナル（{@code
   * Optional.empty()}）を返す（AC2・幽霊行=ID-17を踏襲しない単一削除経路）。
   *
   * @return 永続化すべき絶対数量を保持する書込み用の {@link CartItem}。{@code Optional.empty()} は呼び出し元が {@code
   *     CartRepository#removeItem} を呼ぶべき削除シグナル
   * @throws IllegalArgumentException 数量が在庫数を超える場合（AC5/AC-neg1）
   */
  public Optional<CartItem> updateItem(StockAvailability stock, int quantity) {
    if (quantity <= 0) {
      return Optional.empty();
    }
    if (stock.stockQuantity() <= 0) {
      throw new IllegalArgumentException("Item is out of stock");
    }
    if (quantity > stock.stockQuantity()) {
      throw new IllegalArgumentException("Requested quantity exceeds available stock");
    }
    return Optional.of(CartItem.forWrite(stock.itemId(), quantity, stock.stockQuantity()));
  }

  /**
   * ログイン時マージの1行分（AC3・ID-19・計画フェーズ確定②）。既存数量へ加算し、在庫数を超える分は拒否せず在庫数へクランプする（add/updateとは異なり非拒否）。
   *
   * <p>sec指摘対応（SBD-2）: 既存数量との加算は {@link Math#addExact(int, int)}
   * でオーバーフロー検出する。mergeは非拒否方針のため、オーバーフローした場合は例外化せず「事実上無制限の需要」とみなして在庫数へクランプする。
   *
   * @return 永続化すべき絶対数量を保持する書込み用の {@link CartItem}。クランプ結果が0以下の場合は削除シグナル（{@code Optional.empty()}）
   * @throws IllegalArgumentException {@code requestedQuantity<=0} の場合（#5 AC2・計画フェーズ確定②。黙殺廃止・400相当）
   */
  public Optional<CartItem> mergeLine(StockAvailability stock, int requestedQuantity) {
    if (requestedQuantity <= 0) {
      throw new IllegalArgumentException("Merge line quantity must be a positive integer");
    }
    int clamped;
    try {
      int combined = Math.addExact(stock.currentQuantity(), requestedQuantity);
      clamped = Math.min(combined, stock.stockQuantity());
    } catch (ArithmeticException e) {
      clamped = stock.stockQuantity();
    }
    if (clamped <= 0) {
      return Optional.empty();
    }
    return Optional.of(CartItem.forWrite(stock.itemId(), clamped, stock.stockQuantity()));
  }
}
