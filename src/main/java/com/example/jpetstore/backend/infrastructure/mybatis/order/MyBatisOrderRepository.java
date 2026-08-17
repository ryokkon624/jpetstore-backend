package com.example.jpetstore.backend.infrastructure.mybatis.order;

import com.example.jpetstore.backend.domain.order.NewOrder;
import com.example.jpetstore.backend.domain.order.OrderAddress;
import com.example.jpetstore.backend.domain.order.OrderDetailLine;
import com.example.jpetstore.backend.domain.order.OrderHeader;
import com.example.jpetstore.backend.domain.order.OrderLine;
import com.example.jpetstore.backend.domain.order.OrderRepository;
import com.example.jpetstore.backend.domain.order.OrderSummary;
import com.example.jpetstore.backend.domain.security.CurrentUserProvider;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.OrderHeaderCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.OrderHeaderWriteCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.OrderLineViewCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.OrderLineWriteCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.OrderSummaryCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.OrderCustomMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link OrderRepository} の MyBatis実装（#30・{@code backend-conventions} §9・#29 Cart PoCテンプレ踏襲）。
 *
 * <p>{@link OrderCustomMapper} を保持し、戻り値は常にDomainモデルを返す（書込は生成された注文IDのみ、#9/#10の読取は
 * Entity→Domain変換）。{@code create_user_id}/{@code update_user_id}（WHOカラム）は {@code
 * AuditProgramInterceptor} の自動補完対象外のため、 永続化アクセスの唯一の入口である本クラスが {@link CurrentUserProvider}
 * から明示的に解決する（{@code MyBatisCartRepository} と同じ思想）。
 */
@Repository
public class MyBatisOrderRepository implements OrderRepository {

  private final OrderCustomMapper orderCustomMapper;
  private final CurrentUserProvider currentUserProvider;

  public MyBatisOrderRepository(
      OrderCustomMapper orderCustomMapper, CurrentUserProvider currentUserProvider) {
    this.orderCustomMapper = orderCustomMapper;
    this.currentUserProvider = currentUserProvider;
  }

  @Override
  public Long insertHeader(NewOrder order) {
    Long who = currentUserId();
    OrderAddress billing = order.billing();
    OrderAddress shipping = order.shipping();

    OrderHeaderWriteCustomEntity header = new OrderHeaderWriteCustomEntity();
    header.setUserId(order.userId());
    header.setOrderDate(order.orderDate());
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
    header.setTotalPrice(order.totalPrice());
    header.setBillToFirstName(billing.firstName());
    header.setBillToLastName(billing.lastName());
    header.setShipToFirstName(shipping.firstName());
    header.setShipToLastName(shipping.lastName());
    header.setStatusCode(order.status().getCode());
    header.setCreateUserId(who);
    header.setUpdateUserId(who);

    orderCustomMapper.insertOrderHeader(header);
    return header.getOrderId();
  }

  @Override
  public void insertLine(Long orderId, OrderLine line) {
    Long who = currentUserId();
    OrderLineWriteCustomEntity entity = new OrderLineWriteCustomEntity();
    entity.setOrderId(orderId);
    entity.setLineNum(line.lineNum());
    entity.setItemId(line.itemId());
    entity.setQuantity(line.quantity());
    entity.setUnitPrice(line.unitPrice());
    entity.setCreateUserId(who);
    entity.setUpdateUserId(who);
    orderCustomMapper.insertOrderLine(entity);
  }

  @Override
  public List<OrderSummary> listByUser(Long userId, int offset, int limit) {
    return orderCustomMapper.selectOrdersByUserId(userId, offset, limit).stream()
        .map(MyBatisOrderRepository::toSummary)
        .toList();
  }

  @Override
  public long countByUser(Long userId) {
    return orderCustomMapper.countOrdersByUserId(userId);
  }

  @Override
  public Optional<OrderHeader> findHeaderById(Long orderId) {
    OrderHeaderCustomEntity entity = orderCustomMapper.selectOrderHeaderById(orderId);
    return Optional.ofNullable(entity).map(MyBatisOrderRepository::toHeader);
  }

  @Override
  public List<OrderDetailLine> findLinesByOrderId(Long orderId) {
    return orderCustomMapper.selectOrderLinesByOrderId(orderId).stream()
        .map(MyBatisOrderRepository::toDetailLine)
        .toList();
  }

  private static OrderSummary toSummary(OrderSummaryCustomEntity entity) {
    return new OrderSummary(entity.getOrderId(), entity.getOrderDate(), entity.getTotalPrice());
  }

  private static OrderHeader toHeader(OrderHeaderCustomEntity entity) {
    return new OrderHeader(
        entity.getOrderId(), entity.getUserId(), entity.getOrderDate(), entity.getTotalPrice());
  }

  private static OrderDetailLine toDetailLine(OrderLineViewCustomEntity entity) {
    return new OrderDetailLine(
        entity.getItemId(), entity.getProductName(), entity.getQuantity(), entity.getUnitPrice());
  }

  private Long currentUserId() {
    return currentUserProvider.requireCurrentUser().userId();
  }
}
