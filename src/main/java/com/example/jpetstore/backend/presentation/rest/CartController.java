package com.example.jpetstore.backend.presentation.rest;

import com.example.jpetstore.backend.application.service.CartApplicationService;
import com.example.jpetstore.backend.domain.cart.Cart;
import com.example.jpetstore.backend.domain.cart.CartItem;
import com.example.jpetstore.backend.domain.cart.CartLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * カートAPI（#4 AC1〜AC5・[L2]・AC-neg1）。
 *
 * <p>{@code SecurityConfig} は無変更（{@code /api/cart/**} は既存の {@code anyRequest().authenticated()}
 * 配下＝常に認証必須。非GETはCSRF必須）。カートは常に呼び出し元（JWTプリンシパル）のカートのみを対象とし、URLにcartId等を取らない（IDOR面ゼロ。{@code
 * CartApplicationService}のjavadoc参照）。
 *
 * <p>レスポンスの行 {@code quantity} はユーザ自身の入力であり表示必須のため露出する（ID-28非該当）。一方、在庫数そのもの（qty）は一切含めない（{@code
 * stockStatus}バッジ・{@code exceedsStock}真偽のみ）。
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

  private final CartApplicationService cartApplicationService;

  public CartController(CartApplicationService cartApplicationService) {
    this.cartApplicationService = cartApplicationService;
  }

  @GetMapping
  public CartResponse viewCart() {
    return CartResponse.from(cartApplicationService.viewCart());
  }

  /** カートへの追加（AC1）。{@code quantity} 省略時は既定1（legacyの「既にあれば+1」を一般化）。 */
  @PostMapping("/items")
  public CartResponse addItem(@Valid @RequestBody AddCartItemRequest request) {
    int quantity = request.quantity() == null ? 1 : request.quantity();
    return CartResponse.from(cartApplicationService.addItem(request.itemId(), quantity));
  }

  /** カート内数量の明示更新（AC1）。{@code quantity<=0} は行削除になる（AC2・0=削除セマンティクスは維持）。 */
  @PutMapping("/items/{itemId}")
  public CartResponse updateItem(
      @PathVariable String itemId, @Valid @RequestBody UpdateCartItemRequest request) {
    return CartResponse.from(cartApplicationService.updateItem(itemId, request.quantity()));
  }

  /** カートからの明示削除（AC1）。 */
  @DeleteMapping("/items/{itemId}")
  public CartResponse removeItem(@PathVariable String itemId) {
    return CartResponse.from(cartApplicationService.removeItem(itemId));
  }

  /**
   * ログイン時マージ（AC3・ID-19・計画フェーズ確定②）。未ログイン中にクライアント（localStorage）で保持していたカート行の配列をそのまま受け取る。 itemId
   * が空/欠落の行は無視する（クライアント側の不正/破損データへの防御。厳密なBean Validationは行わず {@code CartApplicationService}
   * 側の防御的処理に委ねる）。
   */
  @PostMapping("/merge")
  public CartResponse merge(@RequestBody List<MergeCartLineRequest> lines) {
    List<CartLine> domainLines =
        lines.stream()
            .filter(line -> line.itemId() != null && !line.itemId().isBlank())
            .map(line -> new CartLine(line.itemId(), line.quantity()))
            .toList();
    return CartResponse.from(cartApplicationService.merge(domainLines));
  }

  /**
   * カート追加要求DTO（Controller層に閉じる）。{@code quantity}
   * は任意（既定1）。指定する場合は正の整数のみ許容する（{@code @Min(1)}・sec指摘対応。nullは検証対象外のため省略時は素通りし、Controller側で既定1に補完する）。
   */
  public record AddCartItemRequest(@NotBlank String itemId, @Min(1) Integer quantity) {}

  /**
   * カート数量更新要求DTO（Controller層に閉じる）。{@code quantity} は必須かつ0以上の整数のみ許容する （#5
   * AC2・計画フェーズ確定②）。{@code @NotNull}=欠落400／{@code @Min(0)}=負数400。0は通過しサービス層で行削除になる
   * （0=削除セマンティクスは維持）。非数値（例: {@code "abc"}）はデシリアライズ時点で {@link
   * org.springframework.http.converter.HttpMessageNotReadableException} となり {@code
   * GlobalExceptionHandler} で400化する。
   */
  public record UpdateCartItemRequest(@NotNull @Min(0) Integer quantity) {}

  /** マージ明細1行分のDTO（Controller層に閉じる）。 */
  public record MergeCartLineRequest(String itemId, int quantity) {}

  /** カート内アイテム応答DTO（Controller層に閉じる）。在庫数そのものは含めない（ID-28）。 */
  public record CartItemResponse(
      String itemId,
      String productId,
      String productName,
      String attribute1,
      int quantity,
      BigDecimal listPrice,
      BigDecimal lineTotal,
      String stockStatus,
      boolean exceedsStock) {
    static CartItemResponse from(CartItem item) {
      return new CartItemResponse(
          item.itemId(),
          item.productId(),
          item.productName(),
          item.attribute1(),
          item.quantity(),
          item.listPrice(),
          item.lineTotal(),
          item.stockStatus().getCode(),
          item.exceedsStock());
    }
  }

  /** カート応答DTO（Controller層に閉じる）。{@code subtotal} はサーバ計算（[L2]）。 */
  public record CartResponse(Long cartId, List<CartItemResponse> items, BigDecimal subtotal) {
    static CartResponse from(Cart cart) {
      return new CartResponse(
          cart.cartId(),
          cart.items().stream().map(CartItemResponse::from).toList(),
          cart.subtotal());
    }
  }
}
