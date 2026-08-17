package com.example.jpetstore.backend.application.service;

import com.example.jpetstore.backend.domain.cart.Cart;
import com.example.jpetstore.backend.domain.cart.CartItem;
import com.example.jpetstore.backend.domain.cart.CartLine;
import com.example.jpetstore.backend.domain.cart.CartRepository;
import com.example.jpetstore.backend.domain.cart.StockAvailability;
import com.example.jpetstore.backend.domain.exception.ResourceNotFoundException;
import com.example.jpetstore.backend.domain.security.CurrentUserProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * カートのユースケース（#4 AC1〜AC5・[L2]・AC-neg1・#29 D2=案A）。
 *
 * <p>カートは常に呼び出し元（{@link CurrentUserProvider}）のカートのみを対象とする。URL/リクエストに cartId 等を取らないため、{@code
 * OwnershipAuthorizationService} による所有者チェックは不要（常にプリンシパルからカートを導出する構造自体がIDOR面をゼロにする。計画フェーズ確定）。
 *
 * <p>永続化アクセスは {@link CartRepository}（Domain層インターフェイス）経由のみで行い、MyBatisの {@code CartCustomMapper} や
 * {@code *CustomEntity}（Infrastructure層）を本クラスへ注入・importしない（#29・{@code backend-conventions}
 * §1/§9）。在庫上限・加算 オーバーフロー・mergeクランプ・0以下削除といった不変条件は {@link Cart}/{@link CartItem}
 * のコマンドメソッドへ移設済みで、本クラスは ユースケースの調整（Repository呼び出しの組み立て・例外の使い分け）のみを担う薄いオーケストレーション層。
 */
@Service
public class CartApplicationService {

  private final CartRepository cartRepository;
  private final CurrentUserProvider currentUserProvider;

  public CartApplicationService(
      CartRepository cartRepository, CurrentUserProvider currentUserProvider) {
    this.cartRepository = cartRepository;
    this.currentUserProvider = currentUserProvider;
  }

  /** カート表示。空でもカートは確保される（{@code CartRepository#findByUserId}）。 */
  @Transactional
  public Cart viewCart() {
    return cartRepository.findByUserId(currentUserId());
  }

  /**
   * カートへの追加（AC1・legacyの「既にあれば+1」を一般化した明示数量版）。既存数量に {@code requestedQuantity} を加算した絶対値の上限チェックは
   * {@link Cart#addItem} が行う。
   *
   * <p>sec指摘対応（SBD-2）: {@code requestedQuantity} は正の整数のみ受理する（DTO側の {@code @Min(1)}
   * に加え、直接呼び出し・バイパス経路への多層防御としてサービス層でも拒否する。Repositoryへは一切アクセスしない）。
   *
   * @throws ResourceNotFoundException 存在しない itemId（AC4/SBD-10）
   * @throws IllegalArgumentException 数量が0以下、在庫切れ、加算後の数量が在庫数を超える、または加算がint
   *     をオーバーフローする場合（AC5/AC-neg1・SBD-2。{@link Cart#addItem} 参照）
   */
  @Transactional
  public Cart addItem(String itemId, int requestedQuantity) {
    if (requestedQuantity <= 0) {
      throw new IllegalArgumentException("Requested quantity must be a positive integer");
    }
    Long userId = currentUserId();
    Cart cart = cartRepository.findByUserId(userId);
    StockAvailability stock = requireStock(cart.cartId(), itemId);
    CartItem line = cart.addItem(stock, requestedQuantity);
    cartRepository.upsertItem(cart.cartId(), line);
    return cartRepository.findByUserId(userId);
  }

  /**
   * カート内数量の明示更新（AC1・{itemId, quantity} API）。数量0以下は行削除する（AC2・幽霊行=ID-17を踏襲しない単一削除経路。判定は {@link
   * Cart#updateItem} が行う）。
   *
   * @throws ResourceNotFoundException 存在しない itemId（AC4/SBD-10）
   * @throws IllegalArgumentException 数量が在庫数を超える場合（AC5/AC-neg1）
   */
  @Transactional
  public Cart updateItem(String itemId, int quantity) {
    Long userId = currentUserId();
    Cart cart = cartRepository.findByUserId(userId);
    StockAvailability stock = requireStock(cart.cartId(), itemId);
    Optional<CartItem> line = cart.updateItem(stock, quantity);
    if (line.isPresent()) {
      cartRepository.upsertItem(cart.cartId(), line.get());
    } else {
      cartRepository.removeItem(cart.cartId(), itemId);
    }
    return cartRepository.findByUserId(userId);
  }

  /** カートからの削除（AC1・AC2）。存在しない行への削除も冪等に成功する（単一表+単一削除経路）。 */
  @Transactional
  public Cart removeItem(String itemId) {
    Long userId = currentUserId();
    Cart cart = cartRepository.findByUserId(userId);
    cartRepository.removeItem(cart.cartId(), itemId);
    return cartRepository.findByUserId(userId);
  }

  /**
   * ログイン時マージ（AC3・ID-19・計画フェーズ確定②）。未ログイン中にクライアント（localStorage）へ保持していたカート行を、サーバーカートへ数量加算でマージする。在庫数を超える分は拒否せず在庫数へクランプする（{@link
   * Cart#mergeLine} 参照。add/updateとは異なり非拒否）。
   *
   * <p>未知の itemId（クライアント側の古いローカルデータ等）は例外にせず無視し、他の行の処理を継続する（防御的。ACに明記が無いための実装判断。詳細は
   * implementation-notes.md）。
   *
   * @throws IllegalArgumentException client行に {@code quantity<=0} が含まれる場合（#5
   *     AC2・計画フェーズ確定②。黙殺廃止・400相当。{@code @Transactional} により部分適用されない）
   */
  @Transactional
  public Cart merge(List<CartLine> clientLines) {
    Long userId = currentUserId();
    Cart cart = cartRepository.findByUserId(userId);

    for (CartLine clientLine : clientLines) {
      if (clientLine.quantity() <= 0) {
        throw new IllegalArgumentException("Merge line quantity must be a positive integer");
      }
      Optional<StockAvailability> stock =
          cartRepository.findStock(cart.cartId(), clientLine.itemId());
      if (stock.isEmpty()) {
        continue;
      }
      Optional<CartItem> line = cart.mergeLine(stock.get(), clientLine.quantity());
      if (line.isPresent()) {
        cartRepository.upsertItem(cart.cartId(), line.get());
      } else {
        cartRepository.removeItem(cart.cartId(), clientLine.itemId());
      }
    }
    return cartRepository.findByUserId(userId);
  }

  private StockAvailability requireStock(Long cartId, String itemId) {
    return cartRepository
        .findStock(cartId, itemId)
        .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemId));
  }

  private Long currentUserId() {
    return currentUserProvider.requireCurrentUser().userId();
  }
}
