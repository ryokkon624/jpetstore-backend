package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

import java.math.BigDecimal;

/**
 * 商品内の在庫アイテム一覧1件を表す参照専用エンティティ（#1・{@code m_item}×{@code t_inventory} JOIN）。
 *
 * <p>{@link #quantity} は在庫バッジ算出（{@code
 * StockStatusCalculator}）専用の内部値であり、Application/Presentation層より上へ そのまま渡してはならない（AC3・在庫数非露出）。
 *
 * <p>MyBatis Generator の生成対象外（カタログはカスタム手書きXMLマッパー。{@code backend-conventions} §9）。
 */
public class ItemSummaryCustomEntity {

  private String itemId;
  private String productId;
  private String attribute1;
  private BigDecimal listPrice;
  private int quantity;

  public String getItemId() {
    return itemId;
  }

  public void setItemId(String itemId) {
    this.itemId = itemId;
  }

  public String getProductId() {
    return productId;
  }

  public void setProductId(String productId) {
    this.productId = productId;
  }

  public String getAttribute1() {
    return attribute1;
  }

  public void setAttribute1(String attribute1) {
    this.attribute1 = attribute1;
  }

  public BigDecimal getListPrice() {
    return listPrice;
  }

  public void setListPrice(BigDecimal listPrice) {
    this.listPrice = listPrice;
  }

  public int getQuantity() {
    return quantity;
  }

  public void setQuantity(int quantity) {
    this.quantity = quantity;
  }
}
