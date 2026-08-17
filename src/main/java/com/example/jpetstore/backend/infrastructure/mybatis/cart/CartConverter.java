package com.example.jpetstore.backend.infrastructure.mybatis.cart;

import com.example.jpetstore.backend.domain.cart.Cart;
import com.example.jpetstore.backend.domain.cart.CartItem;
import com.example.jpetstore.backend.domain.cart.StockAvailability;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartItemCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ItemForCartCustomEntity;
import java.util.List;

/**
 * Cart集約のEntity（Infrastructure層）→Domain変換（#29・{@code backend-conventions} §2/§9）。
 *
 * <p>{@link MyBatisCartRepository}（Repository実装）からのみ呼ぶ。Application層はこの変換を経由せず、常にDomainモデル（{@link
 * Cart}/{@link CartItem}/{@link StockAvailability}）のみを受け取る。
 */
final class CartConverter {

  private CartConverter() {}

  static Cart toCart(Long cartId, List<CartItemCustomEntity> entities) {
    List<CartItem> items = entities.stream().map(CartConverter::toCartItem).toList();
    return Cart.reconstruct(cartId, items);
  }

  private static CartItem toCartItem(CartItemCustomEntity entity) {
    return CartItem.reconstruct(
        entity.getItemId(),
        entity.getProductId(),
        entity.getProductName(),
        entity.getAttribute1(),
        entity.getQuantity(),
        entity.getListPrice(),
        entity.getStockQuantity());
  }

  static StockAvailability toStockAvailability(ItemForCartCustomEntity entity) {
    return new StockAvailability(
        entity.getItemId(), entity.getStockQuantity(), entity.getCurrentQuantity());
  }
}
