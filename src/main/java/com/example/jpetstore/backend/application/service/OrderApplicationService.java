package com.example.jpetstore.backend.application.service;

import com.example.jpetstore.backend.domain.cart.CartItem;
import com.example.jpetstore.backend.domain.cart.CartRepository;
import com.example.jpetstore.backend.domain.concurrency.AffectedRows;
import com.example.jpetstore.backend.domain.enums.OrderStatus;
import com.example.jpetstore.backend.domain.exception.InsufficientStockException;
import com.example.jpetstore.backend.domain.inventory.InventoryRepository;
import com.example.jpetstore.backend.domain.order.NewOrder;
import com.example.jpetstore.backend.domain.order.OrderConfirmation;
import com.example.jpetstore.backend.domain.order.OrderLine;
import com.example.jpetstore.backend.domain.order.OrderRepository;
import com.example.jpetstore.backend.domain.order.PlaceOrderCommand;
import com.example.jpetstore.backend.domain.security.AuthenticatedUser;
import com.example.jpetstore.backend.domain.security.CurrentUserProvider;
import com.example.jpetstore.backend.infrastructure.audit.AuditLogRecorder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 注文確定のユースケース（#8 AC1〜AC6・[L2]・AC-neg1〜3）。
 *
 * <p>数量はサーバ側DBカート（{@link CartRepository#findByCartId}）から読み、価格は確定時点の {@code m_item.list_price}
 * でサーバ再計算する（SBD-2）。username はリクエストではなく {@link CurrentUserProvider} （認証プリンシパル）から導出する（AC-neg3）。
 *
 * <p>在庫はガード付きアトミック減算（architecture-conventions §4.1）で {@code item_id} 昇順に固定順で引き当て、
 * 1品でも不足すれば全体をロールバックする（{@code @Transactional}・all-or-nothing）。{@code CartRepository#findByCartId}
 * は既に {@code ORDER BY ci.item_id} で返すため、そのままの順序でループすれば 昇順固定順減算になる（デッドロック回避）。
 *
 * <p>#30（O1=案A）: rich な Order 集約は作らず、並行オーケストレーション（{@code @Transactional}・item_id昇順固定順ループ・
 * ガード減算＋{@code AffectedRows.requireUpdated}・カート全クリア・成功/失敗監査）は本Serviceに残す。{@link
 * CartRepository}/{@link OrderRepository}/{@link InventoryRepository}（Domain層）経由へ retrofit し、
 * {@code CartCustomMapper}/{@code OrderCustomMapper}/{@code InventoryCustomMapper} 直呼びを解消した（{@code
 * backend-conventions} §9）。
 *
 * <p>{@code application.service} 配下に置くことで {@code ProgramContextAspect} が {@code
 * OrderApplicationService#placeOrder} を機能識別子として WHO カラム（{@code create_program}/{@code
 * update_program}）へ自動付与する。
 */
@Service
public class OrderApplicationService {

  private static final String ACTION_ORDER_CREATE = "ORDER_CREATE";
  private static final String TARGET_TYPE_ORDER = "ORDER";
  private static final String RESULT_SUCCESS = "SUCCESS";
  private static final String RESULT_FAILURE = "FAILURE";

  private final CartRepository cartRepository;
  private final OrderRepository orderRepository;
  private final InventoryRepository inventoryRepository;
  private final CurrentUserProvider currentUserProvider;
  private final AuditLogRecorder auditLogRecorder;

  public OrderApplicationService(
      CartRepository cartRepository,
      OrderRepository orderRepository,
      InventoryRepository inventoryRepository,
      CurrentUserProvider currentUserProvider,
      AuditLogRecorder auditLogRecorder) {
    this.cartRepository = cartRepository;
    this.orderRepository = orderRepository;
    this.inventoryRepository = inventoryRepository;
    this.currentUserProvider = currentUserProvider;
    this.auditLogRecorder = auditLogRecorder;
  }

  /**
   * 注文を確定する。カート読取→サーバ再計算→注文ヘッダINSERT→（item_id昇順で）在庫ガード減算＋明細INSERT→
   * カート全クリア→成功監査、の一連を単一トランザクションで行う。在庫不足・空カートは {@link InsufficientStockException}
   * となり全体がロールバックされる。失敗時は主トランザクションと独立した別 トランザクション（{@link
   * AuditLogRecorder#recordStateChangeIndependently}・REQUIRES_NEW）で FAILURE
   * 監査を記録してから再送出する（AC6・計画フェーズ確定③。主txのロールバックに巻き込まれない）。
   *
   * @throws InsufficientStockException 在庫不足（affected rows==0）または空カートの場合（409相当）
   */
  @Transactional
  public OrderConfirmation placeOrder(PlaceOrderCommand command) {
    AuthenticatedUser user = currentUserProvider.requireCurrentUser();
    Long userId = user.userId();
    Long cartId = cartRepository.ensureCart(userId);
    List<CartItem> cartItems = cartRepository.findByCartId(cartId).items();

    try {
      if (cartItems.isEmpty()) {
        throw new InsufficientStockException(null);
      }

      BigDecimal totalPrice = calculateTotal(cartItems);
      NewOrder newOrder =
          new NewOrder(
              userId,
              command.billing(),
              command.effectiveShipping(),
              totalPrice,
              OrderStatus.NEW,
              LocalDate.now());
      Long orderId = orderRepository.insertHeader(newOrder);
      insertOrderLinesWithGuardedDecrement(orderId, cartItems);
      cartRepository.clearItems(cartId);

      auditLogRecorder.recordStateChange(
          ACTION_ORDER_CREATE,
          TARGET_TYPE_ORDER,
          orderId.toString(),
          RESULT_SUCCESS,
          Map.of("total", totalPrice, "itemCount", cartItems.size()));

      return new OrderConfirmation(orderId, totalPrice);
    } catch (InsufficientStockException e) {
      auditLogRecorder.recordStateChangeIndependently(
          ACTION_ORDER_CREATE, null, null, RESULT_FAILURE, failureDetail(e));
      throw e;
    }
  }

  private BigDecimal calculateTotal(List<CartItem> cartItems) {
    return cartItems.stream()
        .map(item -> item.listPrice().multiply(BigDecimal.valueOf(item.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** item_id昇順（{@code findByCartId} の順序）でガード減算→明細INSERTを行う（arch §4.1・固定順でデッドロック回避）。 */
  private void insertOrderLinesWithGuardedDecrement(Long orderId, List<CartItem> cartItems) {
    int lineNum = 1;
    for (CartItem item : cartItems) {
      int rows = inventoryRepository.decrease(item.itemId(), item.quantity());
      AffectedRows.requireUpdated(rows, () -> new InsufficientStockException(item.itemId()));

      OrderLine line = new OrderLine(item.itemId(), lineNum++, item.quantity(), item.listPrice());
      orderRepository.insertLine(orderId, line);
    }
  }

  private Map<String, Object> failureDetail(InsufficientStockException e) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("reason", e.itemId() == null ? "EMPTY_CART" : "INSUFFICIENT_STOCK");
    detail.put("itemId", e.itemId());
    return detail;
  }
}
