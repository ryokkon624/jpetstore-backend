package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

import java.math.BigDecimal;

/**
 * {@code t_order_line}（注文明細）の書き込み用パラメータエンティティ（#8）。
 *
 * <p>{@link #lineNum} は呼び出し元（{@code OrderApplicationService}）が item_id 昇順（{@code
 * CartCustomMapper#selectCartItems} の順序）で 1 から採番する。{@link #unitPrice} は確定時点の {@code
 * m_item.list_price}（サーバ再計算・AC1/AC4）。
 */
public class OrderLineWriteCustomEntity {

  private Long orderId;
  private int lineNum;
  private String itemId;
  private int quantity;
  private BigDecimal unitPrice;
  private Long createUserId;
  private String createProgram;
  private Long updateUserId;
  private String updateProgram;

  public Long getOrderId() {
    return orderId;
  }

  public void setOrderId(Long orderId) {
    this.orderId = orderId;
  }

  public int getLineNum() {
    return lineNum;
  }

  public void setLineNum(int lineNum) {
    this.lineNum = lineNum;
  }

  public String getItemId() {
    return itemId;
  }

  public void setItemId(String itemId) {
    this.itemId = itemId;
  }

  public int getQuantity() {
    return quantity;
  }

  public void setQuantity(int quantity) {
    this.quantity = quantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public void setUnitPrice(BigDecimal unitPrice) {
    this.unitPrice = unitPrice;
  }

  public Long getCreateUserId() {
    return createUserId;
  }

  public void setCreateUserId(Long createUserId) {
    this.createUserId = createUserId;
  }

  public String getCreateProgram() {
    return createProgram;
  }

  public void setCreateProgram(String createProgram) {
    this.createProgram = createProgram;
  }

  public Long getUpdateUserId() {
    return updateUserId;
  }

  public void setUpdateUserId(Long updateUserId) {
    this.updateUserId = updateUserId;
  }

  public String getUpdateProgram() {
    return updateProgram;
  }

  public void setUpdateProgram(String updateProgram) {
    this.updateProgram = updateProgram;
  }
}
