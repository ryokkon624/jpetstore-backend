package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

import java.math.BigDecimal;

/**
 * 注文詳細閲覧（#10 AC2）の明細1行を表す参照専用エンティティ（{@code t_order_line ⋈ m_item ⋈ m_product} JOIN）。
 *
 * <p>{@link #productName} はJOINで補完する商品名（ID-24・非等価改善）。既存の書込用 {@code OrderLineWriteCustomEntity}
 * とは別クラス（読取専用・命名は"View"で区別・{@code backend-conventions} §9）。MyBatis Generator の生成対象外。
 */
public class OrderLineViewCustomEntity {

  private String itemId;
  private String productName;
  private int quantity;
  private BigDecimal unitPrice;

  public String getItemId() {
    return itemId;
  }

  public void setItemId(String itemId) {
    this.itemId = itemId;
  }

  public String getProductName() {
    return productName;
  }

  public void setProductName(String productName) {
    this.productName = productName;
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
}
