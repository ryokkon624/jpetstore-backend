package com.example.jpetstore.backend.infrastructure.mybatis.order;

import com.example.jpetstore.backend.domain.order.NewOrder;
import com.example.jpetstore.backend.domain.order.OrderAddress;
import com.example.jpetstore.backend.domain.order.OrderLine;
import com.example.jpetstore.backend.domain.order.OrderRepository;
import com.example.jpetstore.backend.domain.security.CurrentUserProvider;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.OrderHeaderWriteCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.OrderLineWriteCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.OrderCustomMapper;
import org.springframework.stereotype.Repository;

/**
 * {@link OrderRepository} の MyBatis実装（#30・{@code backend-conventions} §9・#29 Cart PoCテンプレ踏襲）。
 *
 * <p>{@link OrderCustomMapper} を保持し、戻り値は常にDomainモデル（生成された注文IDのみ）を返す。{@code create_user_id}/{@code
 * update_user_id}（WHOカラム）は {@code AuditProgramInterceptor} の自動補完対象外のため、 永続化アクセスの唯一の入口である本クラスが
 * {@link CurrentUserProvider} から明示的に解決する（{@code MyBatisCartRepository} と同じ思想）。
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

  private Long currentUserId() {
    return currentUserProvider.requireCurrentUser().userId();
  }
}
