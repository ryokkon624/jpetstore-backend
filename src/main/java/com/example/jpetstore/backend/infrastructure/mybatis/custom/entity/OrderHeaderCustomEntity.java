package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 注文詳細閲覧（#10）で所有者解決と応答の両方に使うヘッダ参照専用エンティティ（{@code t_order} 単独SELECT・JOIN無し）。
 *
 * <p>{@link #userId} は所有者判定（{@code OwnershipAuthorizationService#assertOwner}）に使う「サーバー側で解決した真の所有者」。
 * MyBatis Generator の生成対象外（Order 読取はカスタム手書きXMLマッパー。{@code backend-conventions} §9）。
 */
public class OrderHeaderCustomEntity {

  private Long orderId;
  private Long userId;
  private LocalDate orderDate;
  private BigDecimal totalPrice;

  public Long getOrderId() {
    return orderId;
  }

  public void setOrderId(Long orderId) {
    this.orderId = orderId;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public LocalDate getOrderDate() {
    return orderDate;
  }

  public void setOrderDate(LocalDate orderDate) {
    this.orderDate = orderDate;
  }

  public BigDecimal getTotalPrice() {
    return totalPrice;
  }

  public void setTotalPrice(BigDecimal totalPrice) {
    this.totalPrice = totalPrice;
  }
}
