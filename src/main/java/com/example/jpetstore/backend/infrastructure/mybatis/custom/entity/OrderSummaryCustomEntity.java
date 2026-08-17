package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 注文履歴一覧（#9）の1行を表す参照専用エンティティ（{@code t_order} 単独SELECT・JOIN無し）。
 *
 * <p>MyBatis Generator の生成対象外（Order 読取はカスタム手書きXMLマッパー。{@code backend-conventions} §9）。
 */
public class OrderSummaryCustomEntity {

  private Long orderId;
  private LocalDate orderDate;
  private BigDecimal totalPrice;

  public Long getOrderId() {
    return orderId;
  }

  public void setOrderId(Long orderId) {
    this.orderId = orderId;
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
