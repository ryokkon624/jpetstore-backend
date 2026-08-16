package com.example.jpetstore.backend.application.service;

import com.example.jpetstore.backend.domain.cart.Cart;
import com.example.jpetstore.backend.domain.cart.CartItem;
import com.example.jpetstore.backend.domain.cart.CartLine;
import com.example.jpetstore.backend.domain.catalog.StockStatusCalculator;
import com.example.jpetstore.backend.domain.exception.ResourceNotFoundException;
import com.example.jpetstore.backend.domain.security.CurrentUserProvider;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartHeaderCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartItemCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartItemWriteCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ItemForCartCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.CartCustomMapper;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * カートのユースケース（#4 AC1〜AC5・[L2]・AC-neg1）。
 *
 * <p>カートは常に呼び出し元（{@link CurrentUserProvider}）のカートのみを対象とする。URL/リクエストに cartId 等を取らないため、{@code
 * OwnershipAuthorizationService} による所有者チェックは不要（常にプリンシパルからカートを導出する構造自体がIDOR面をゼロにする。計画フェーズ確定）。
 *
 * <p>在庫上限（=在庫数）の強制は本クラス内でのみ在庫qty（{@link
 * ItemForCartCustomEntity#getStockQuantity()}）を扱い、Application/Presentation層の戻り値（{@link
 * CartItem}）には生の在庫qtyを一切含めない（ID-28・在庫数非露出）。add/update（ユーザーの明示操作）は超過を拒否（400相当の {@link
 * IllegalArgumentException}）、merge（ログイン時の自動マージ）は拒否せず在庫数へクランプする（計画フェーズ確定②）。
 */
@Service
public class CartApplicationService {

  private final CartCustomMapper cartCustomMapper;
  private final CurrentUserProvider currentUserProvider;

  public CartApplicationService(
      CartCustomMapper cartCustomMapper, CurrentUserProvider currentUserProvider) {
    this.cartCustomMapper = cartCustomMapper;
    this.currentUserProvider = currentUserProvider;
  }

  /** カート表示。空でもカートは確保される（{@code ensureCart}）。 */
  @Transactional
  public Cart viewCart() {
    Long cartId = ensureCartFor(currentUserId());
    return toCart(cartId);
  }

  /**
   * カートへの追加（AC1・legacyの「既にあれば+1」を一般化した明示数量版）。既存数量に {@code requestedQuantity}
   * を加算した絶対値を上限チェックのうえ書き込む。
   *
   * <p>sec指摘対応（SBD-2）: {@code requestedQuantity} は正の整数のみ受理する（DTO側の {@code @Min(1)}
   * に加え、直接呼び出し・バイパス経路への多層防御としてサービス層でも拒否する）。既存数量との加算は {@link Math#addExact(int, int)}
   * でオーバーフロー検出する。オーバーフロー（intラップにより負の巨大な値が {@code newQuantity > stockQuantity}
   * の上限チェックを迂回し、負の数量が永続化されてしまう不具合）を 例外化して防ぐ。
   *
   * @throws ResourceNotFoundException 存在しない itemId（AC4/SBD-10）
   * @throws IllegalArgumentException 数量が0以下、在庫切れ、加算後の数量が在庫数を超える、または加算がint
   *     をオーバーフローする場合（AC5/AC-neg1・SBD-2）
   */
  @Transactional
  public Cart addItem(String itemId, int requestedQuantity) {
    if (requestedQuantity <= 0) {
      throw new IllegalArgumentException("Requested quantity must be a positive integer");
    }
    Long userId = currentUserId();
    Long cartId = ensureCartFor(userId);
    ItemForCartCustomEntity lookup = requireItemForCart(itemId, cartId);

    if (lookup.getStockQuantity() <= 0) {
      throw new IllegalArgumentException("Item is out of stock");
    }
    int newQuantity;
    try {
      newQuantity = Math.addExact(lookup.getCurrentQuantity(), requestedQuantity);
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException("Requested quantity exceeds available stock");
    }
    if (newQuantity > lookup.getStockQuantity()) {
      throw new IllegalArgumentException("Requested quantity exceeds available stock");
    }
    applyQuantity(cartId, itemId, newQuantity, userId);
    return toCart(cartId);
  }

  /**
   * カート内数量の明示更新（AC1・{itemId, quantity} API）。数量0以下は行削除する（AC2・幽霊行=ID-17を踏襲しない単一削除経路）。
   *
   * @throws ResourceNotFoundException 存在しない itemId（AC4/SBD-10）
   * @throws IllegalArgumentException 数量が在庫数を超える場合（AC5/AC-neg1）
   */
  @Transactional
  public Cart updateItem(String itemId, int quantity) {
    Long userId = currentUserId();
    Long cartId = ensureCartFor(userId);
    ItemForCartCustomEntity lookup = requireItemForCart(itemId, cartId);

    if (quantity <= 0) {
      cartCustomMapper.deleteCartItem(cartId, itemId);
      return toCart(cartId);
    }
    if (lookup.getStockQuantity() <= 0) {
      throw new IllegalArgumentException("Item is out of stock");
    }
    if (quantity > lookup.getStockQuantity()) {
      throw new IllegalArgumentException("Requested quantity exceeds available stock");
    }
    applyQuantity(cartId, itemId, quantity, userId);
    return toCart(cartId);
  }

  /** カートからの削除（AC1・AC2）。存在しない行への削除も冪等に成功する（単一表+単一削除経路）。 */
  @Transactional
  public Cart removeItem(String itemId) {
    Long cartId = ensureCartFor(currentUserId());
    cartCustomMapper.deleteCartItem(cartId, itemId);
    return toCart(cartId);
  }

  /**
   * ログイン時マージ（AC3・ID-19・計画フェーズ確定②）。未ログイン中にクライアント（localStorage）へ保持していたカート行を、サーバーカートへ数量加算でマージする。在庫数を超える分は拒否せず在庫数へクランプする（add/updateとは異なり非拒否）。
   *
   * <p>未知の itemId（クライアント側の古いローカルデータ等）は例外にせず無視し、他の行の処理を継続する（防御的。ACに明記が無いための実装判断。詳細は
   * implementation-notes.md）。
   *
   * <p>sec指摘対応（SBD-2）: 既存数量との加算は {@link Math#addExact(int, int)}
   * でオーバーフロー検出する。mergeは非拒否方針のため、オーバーフローした場合は例外化せず「事実上無制限の需要」とみなして在庫数へクランプする（他の正常な行の処理も継続する）。
   *
   * @throws IllegalArgumentException client行に {@code quantity<=0} が含まれる場合（#5
   *     AC2・計画フェーズ確定②。黙殺廃止・400相当。{@code @Transactional} により部分適用されない）
   */
  @Transactional
  public Cart merge(List<CartLine> clientLines) {
    Long userId = currentUserId();
    Long cartId = ensureCartFor(userId);

    for (CartLine line : clientLines) {
      if (line.quantity() <= 0) {
        throw new IllegalArgumentException("Merge line quantity must be a positive integer");
      }
      ItemForCartCustomEntity lookup = cartCustomMapper.selectItemForCart(line.itemId(), cartId);
      if (lookup == null) {
        continue;
      }
      int clamped;
      try {
        int combined = Math.addExact(lookup.getCurrentQuantity(), line.quantity());
        clamped = Math.min(combined, lookup.getStockQuantity());
      } catch (ArithmeticException e) {
        clamped = lookup.getStockQuantity();
      }
      if (clamped <= 0) {
        cartCustomMapper.deleteCartItem(cartId, line.itemId());
      } else {
        applyQuantity(cartId, line.itemId(), clamped, userId);
      }
    }
    return toCart(cartId);
  }

  private ItemForCartCustomEntity requireItemForCart(String itemId, Long cartId) {
    ItemForCartCustomEntity lookup = cartCustomMapper.selectItemForCart(itemId, cartId);
    if (lookup == null) {
      throw new ResourceNotFoundException("Item not found: " + itemId);
    }
    return lookup;
  }

  private void applyQuantity(Long cartId, String itemId, int quantity, Long userId) {
    CartItemWriteCustomEntity write = new CartItemWriteCustomEntity();
    write.setCartId(cartId);
    write.setItemId(itemId);
    write.setQuantity(quantity);
    write.setCreateUserId(userId);
    write.setUpdateUserId(userId);
    cartCustomMapper.upsertCartItemQuantity(write);
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

  private Cart toCart(Long cartId) {
    List<CartItem> items =
        cartCustomMapper.selectCartItems(cartId).stream().map(this::toCartItem).toList();
    return Cart.of(cartId, items);
  }

  private CartItem toCartItem(CartItemCustomEntity entity) {
    BigDecimal lineTotal = entity.getListPrice().multiply(BigDecimal.valueOf(entity.getQuantity()));
    return new CartItem(
        entity.getItemId(),
        entity.getProductId(),
        entity.getProductName(),
        entity.getAttribute1(),
        entity.getQuantity(),
        entity.getListPrice(),
        lineTotal,
        StockStatusCalculator.of(entity.getStockQuantity()),
        entity.getQuantity() > entity.getStockQuantity());
  }
}
