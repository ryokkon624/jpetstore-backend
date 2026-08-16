package com.example.jpetstore.backend.domain.cart;

import java.math.BigDecimal;
import java.util.List;

/**
 * カートを表す参照系ドメインモデル（#4 AC1）。
 *
 * <p>{@link #subtotal} はサーバ計算（{@code Σ(listPrice × quantity)}・[L2] 旧同値・SBD-2/13）。{@link #of} が
 * {@link CartItem#lineTotal} の合算で算出するため、呼び出し元（{@code CartApplicationService}）は個々の {@link CartItem}
 * を作った後この工場メソッドを呼ぶだけでよい。
 */
public record Cart(Long cartId, List<CartItem> items, BigDecimal subtotal) {

  public Cart {
    items = List.copyOf(items);
  }

  public static Cart of(Long cartId, List<CartItem> items) {
    BigDecimal subtotal =
        items.stream().map(CartItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new Cart(cartId, items, subtotal);
  }
}
