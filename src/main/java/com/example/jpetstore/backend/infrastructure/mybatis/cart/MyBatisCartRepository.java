package com.example.jpetstore.backend.infrastructure.mybatis.cart;

import com.example.jpetstore.backend.domain.cart.Cart;
import com.example.jpetstore.backend.domain.cart.CartItem;
import com.example.jpetstore.backend.domain.cart.CartRepository;
import com.example.jpetstore.backend.domain.cart.StockAvailability;
import com.example.jpetstore.backend.domain.security.CurrentUserProvider;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartHeaderCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartItemWriteCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ItemForCartCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.CartCustomMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link CartRepository} の MyBatis実装（#29・本コードベース初のRepository実装。参照実装）。
 *
 * <p>{@link CartCustomMapper} と {@link CartConverter} を保持し、戻り値は常にDomainモデルのみ（{@code *CustomEntity}
 * をApplication層へ出さない・{@code backend-conventions} §1/§9）。
 *
 * <p>{@code create_user_id}/{@code update_user_id}（WHOカラム）は {@code AuditProgramInterceptor}
 * の自動補完対象外のため、永続化アクセスの唯一の入口である本クラスが {@link CurrentUserProvider} から明示的に解決する（従来 {@code
 * CartApplicationService} が担っていた責務を、永続化の関心事としてRepository層へ集約した）。
 */
@Repository
public class MyBatisCartRepository implements CartRepository {

  private final CartCustomMapper cartCustomMapper;
  private final CurrentUserProvider currentUserProvider;

  public MyBatisCartRepository(
      CartCustomMapper cartCustomMapper, CurrentUserProvider currentUserProvider) {
    this.cartCustomMapper = cartCustomMapper;
    this.currentUserProvider = currentUserProvider;
  }

  @Override
  public Cart findByUserId(Long userId) {
    Long cartId = ensureCartFor(userId);
    return CartConverter.toCart(cartId, cartCustomMapper.selectCartItems(cartId));
  }

  @Override
  public Optional<StockAvailability> findStock(Long cartId, String itemId) {
    ItemForCartCustomEntity entity = cartCustomMapper.selectItemForCart(itemId, cartId);
    return Optional.ofNullable(entity).map(CartConverter::toStockAvailability);
  }

  @Override
  public void upsertItem(Long cartId, CartItem line) {
    Long userId = currentUserId();
    CartItemWriteCustomEntity write = new CartItemWriteCustomEntity();
    write.setCartId(cartId);
    write.setItemId(line.itemId());
    write.setQuantity(line.quantity());
    write.setCreateUserId(userId);
    write.setUpdateUserId(userId);
    cartCustomMapper.upsertCartItemQuantity(write);
  }

  @Override
  public void removeItem(Long cartId, String itemId) {
    cartCustomMapper.deleteCartItem(cartId, itemId);
  }

  private Long ensureCartFor(Long userId) {
    CartHeaderCustomEntity header = new CartHeaderCustomEntity();
    header.setUserId(userId);
    header.setCreateUserId(userId);
    header.setUpdateUserId(userId);
    cartCustomMapper.ensureCart(header);
    return header.getCartId();
  }

  private Long currentUserId() {
    return currentUserProvider.requireCurrentUser().userId();
  }
}
