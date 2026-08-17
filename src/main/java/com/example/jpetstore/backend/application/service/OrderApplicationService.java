package com.example.jpetstore.backend.application.service;

import com.example.jpetstore.backend.domain.concurrency.AffectedRows;
import com.example.jpetstore.backend.domain.enums.OrderStatus;
import com.example.jpetstore.backend.domain.exception.InsufficientStockException;
import com.example.jpetstore.backend.domain.order.OrderAddress;
import com.example.jpetstore.backend.domain.order.OrderConfirmation;
import com.example.jpetstore.backend.domain.order.PlaceOrderCommand;
import com.example.jpetstore.backend.domain.security.AuthenticatedUser;
import com.example.jpetstore.backend.domain.security.CurrentUserProvider;
import com.example.jpetstore.backend.infrastructure.audit.AuditLogRecorder;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartHeaderCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartItemCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.InventoryDecreaseCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.OrderHeaderWriteCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.OrderLineWriteCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.CartCustomMapper;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.InventoryCustomMapper;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.OrderCustomMapper;
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
 * <p>数量はサーバ側DBカート（{@link CartCustomMapper#selectCartItems}）から読み、価格は確定時点の {@code m_item.list_price}
 * でサーバ再計算する（SBD-2）。username はリクエストではなく {@link CurrentUserProvider} （認証プリンシパル）から導出する（AC-neg3）。
 *
 * <p>在庫はガード付きアトミック減算（architecture-conventions §4.1）で {@code item_id} 昇順に固定順で引き当て、
 * 1品でも不足すれば全体をロールバックする（{@code @Transactional}・all-or-nothing）。{@code
 * CartCustomMapper#selectCartItems} は既に {@code ORDER BY ci.item_id} で返すため、そのままの順序でループすれば
 * 昇順固定順減算になる（デッドロック回避）。
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

  private final CartCustomMapper cartCustomMapper;
  private final OrderCustomMapper orderCustomMapper;
  private final InventoryCustomMapper inventoryCustomMapper;
  private final CurrentUserProvider currentUserProvider;
  private final AuditLogRecorder auditLogRecorder;

  public OrderApplicationService(
      CartCustomMapper cartCustomMapper,
      OrderCustomMapper orderCustomMapper,
      InventoryCustomMapper inventoryCustomMapper,
      CurrentUserProvider currentUserProvider,
      AuditLogRecorder auditLogRecorder) {
    this.cartCustomMapper = cartCustomMapper;
    this.orderCustomMapper = orderCustomMapper;
    this.inventoryCustomMapper = inventoryCustomMapper;
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
    Long cartId = ensureCartFor(userId);
    List<CartItemCustomEntity> cartItems = cartCustomMapper.selectCartItems(cartId);

    try {
      if (cartItems.isEmpty()) {
        throw new InsufficientStockException(null);
      }

      BigDecimal totalPrice = calculateTotal(cartItems);
      Long orderId = insertOrderHeader(command, userId, totalPrice);
      insertOrderLinesWithGuardedDecrement(orderId, cartItems, userId);
      cartCustomMapper.deleteCartItems(cartId);

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

  private BigDecimal calculateTotal(List<CartItemCustomEntity> cartItems) {
    return cartItems.stream()
        .map(item -> item.getListPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private Long insertOrderHeader(PlaceOrderCommand command, Long userId, BigDecimal totalPrice) {
    OrderAddress billing = command.billing();
    OrderAddress shipping = command.effectiveShipping();

    OrderHeaderWriteCustomEntity header = new OrderHeaderWriteCustomEntity();
    header.setUserId(userId);
    header.setOrderDate(LocalDate.now());
    header.setShipAddress1(shipping.address1());
    header.setShipAddress2(shipping.address2());
    header.setShipCity(shipping.city());
    header.setShipState(shipping.state());
    header.setShipPostalCode(shipping.postalCode());
    header.setShipCountry(shipping.country());
    header.setBillAddress1(billing.address1());
    header.setBillAddress2(billing.address2());
    header.setBillCity(billing.city());
    header.setBillState(billing.state());
    header.setBillPostalCode(billing.postalCode());
    header.setBillCountry(billing.country());
    header.setTotalPrice(totalPrice);
    header.setBillToFirstName(billing.firstName());
    header.setBillToLastName(billing.lastName());
    header.setShipToFirstName(shipping.firstName());
    header.setShipToLastName(shipping.lastName());
    header.setStatusCode(OrderStatus.NEW.getCode());
    header.setCreateUserId(userId);
    header.setUpdateUserId(userId);

    orderCustomMapper.insertOrderHeader(header);
    return header.getOrderId();
  }

  /** item_id昇順（{@code selectCartItems} の順序）でガード減算→明細INSERTを行う（arch §4.1・固定順でデッドロック回避）。 */
  private void insertOrderLinesWithGuardedDecrement(
      Long orderId, List<CartItemCustomEntity> cartItems, Long userId) {
    int lineNum = 1;
    for (CartItemCustomEntity item : cartItems) {
      InventoryDecreaseCustomEntity decrease = new InventoryDecreaseCustomEntity();
      decrease.setItemId(item.getItemId());
      decrease.setQuantity(item.getQuantity());
      decrease.setUpdateUserId(userId);
      int rows = inventoryCustomMapper.decreaseInventory(decrease);
      AffectedRows.requireUpdated(rows, () -> new InsufficientStockException(item.getItemId()));

      OrderLineWriteCustomEntity line = new OrderLineWriteCustomEntity();
      line.setOrderId(orderId);
      line.setLineNum(lineNum++);
      line.setItemId(item.getItemId());
      line.setQuantity(item.getQuantity());
      line.setUnitPrice(item.getListPrice());
      line.setCreateUserId(userId);
      line.setUpdateUserId(userId);
      orderCustomMapper.insertOrderLine(line);
    }
  }

  private Map<String, Object> failureDetail(InsufficientStockException e) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("reason", e.itemId() == null ? "EMPTY_CART" : "INSUFFICIENT_STOCK");
    detail.put("itemId", e.itemId());
    return detail;
  }

  private Long ensureCartFor(Long userId) {
    CartHeaderCustomEntity header = new CartHeaderCustomEntity();
    header.setUserId(userId);
    header.setCreateUserId(userId);
    header.setUpdateUserId(userId);
    cartCustomMapper.ensureCart(header);
    return header.getCartId();
  }
}
