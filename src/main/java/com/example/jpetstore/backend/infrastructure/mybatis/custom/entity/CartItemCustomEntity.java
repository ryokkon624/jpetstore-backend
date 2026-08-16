package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

import java.math.BigDecimal;

/**
 * カート内アイテム1件を表す参照専用エンティティ（#4・{@code t_cart_item}×{@code m_item}×{@code m_product}×{@code
 * t_inventory} JOIN）。
 *
 * <p>{@link #stockQuantity} は在庫バッジ・超過判定算出（{@code StockStatusCalculator}・{@code
 * CartApplicationService}） 専用の内部値であり、Application/Presentation層より上へそのまま渡してはならない（ID-28・在庫数非露出）。
 *
 * <p>MyBatis Generator の生成対象外（カートはカスタム手書きXMLマッパー。{@code backend-conventions} §9）。
 */
public class CartItemCustomEntity {

  private String itemId;
  private String productId;
  private String productName;
  private String attribute1;
  private BigDecimal listPrice;
  private int quantity;
  private int stockQuantity;

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

  public String getProductName() {
    return productName;
  }

  public void setProductName(String productName) {
    this.productName = productName;
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

  public int getStockQuantity() {
    return stockQuantity;
  }

  public void setStockQuantity(int stockQuantity) {
    this.stockQuantity = stockQuantity;
  }
}
