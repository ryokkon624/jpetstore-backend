package com.example.jpetstore.backend.presentation.rest;

import com.example.jpetstore.backend.application.service.OrderApplicationService;
import com.example.jpetstore.backend.domain.order.OrderAddress;
import com.example.jpetstore.backend.domain.order.OrderConfirmation;
import com.example.jpetstore.backend.domain.order.PlaceOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 注文確定API（#8 AC1〜AC6・[L2]・AC-neg1〜3）。
 *
 * <p>{@code SecurityConfig} は無変更（{@code /api/orders} は既存の {@code anyRequest().authenticated()}
 * 配下＝常に認証必須。非GETはCSRF必須＝as-isのGET確定リンク{@code newOrder.do?confirmed=true}を廃止。AC4）。
 *
 * <p>リクエストは配送/請求先住所・別配送フラグのみを受理する（SBD-2アローリスト）。数量・価格・username
 * フィールドは持たない（数量はDBカート、価格はサーバ再計算、usernameは認証プリンシパルからそれぞれ導出する ため受け取る必要が無い＝構造的にAC-neg1/AC-neg3を担保する）。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final OrderApplicationService orderApplicationService;

  public OrderController(OrderApplicationService orderApplicationService) {
    this.orderApplicationService = orderApplicationService;
  }

  /** 注文確定（AC4・非冪等POST）。成功時は 201 Created で注文番号・サーバ再計算合計のみを返す(計画フェーズ確定②)。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public OrderConfirmationResponse placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
    OrderConfirmation confirmation = orderApplicationService.placeOrder(request.toCommand());
    return OrderConfirmationResponse.from(confirmation);
  }

  /**
   * 配送/請求先住所1件分のリクエストDTO（Controller層に閉じる）。{@code t_order} の永続列に対応する項目のみを持つ。 frontendの{@code
   * Address}型はemail/phoneも含むが、{@code t_order}に列が無く非永続のため本DTOには含めない
   * （余分なJSONフィールドとして送られても無視される。計画フェーズ確定）。
   */
  public record OrderAddressRequest(
      @NotBlank String firstName,
      @NotBlank String lastName,
      @NotBlank String address1,
      String address2,
      @NotBlank String city,
      @NotBlank String state,
      @NotBlank String postalCode,
      @NotBlank String country) {

    OrderAddress toDomain() {
      return new OrderAddress(
          firstName, lastName, address1, address2, city, state, postalCode, country);
    }
  }

  /**
   * 注文確定リクエストDTO（Controller層に閉じる・SBD-2アローリスト）。{@code billing} は必須。{@code shipping} は {@code
   * useSeparateShipping=true} のときのみ使われる（{@code false} の場合は billing がそのまま配送先になる。 サービス層の {@code
   * PlaceOrderCommand#effectiveShipping()} が解決する）。
   */
  public record PlaceOrderRequest(
      @NotNull @Valid OrderAddressRequest billing,
      OrderAddressRequest shipping,
      boolean useSeparateShipping) {

    PlaceOrderCommand toCommand() {
      return new PlaceOrderCommand(
          billing.toDomain(), shipping == null ? null : shipping.toDomain(), useSeparateShipping);
    }
  }

  /** 注文確定結果DTO（Controller層に閉じる）。完了画面が必要とする最小限（注文番号・サーバ再計算合計）のみ。 */
  public record OrderConfirmationResponse(Long orderId, BigDecimal totalPrice) {
    static OrderConfirmationResponse from(OrderConfirmation confirmation) {
      return new OrderConfirmationResponse(confirmation.orderId(), confirmation.totalPrice());
    }
  }
}
