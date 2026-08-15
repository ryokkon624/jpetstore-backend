package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

/**
 * {@code m_product} 1件を表す参照専用エンティティ（#1）。
 *
 * <p>MyBatis Generator の生成対象外（カタログはカスタム手書きXMLマッパー。{@code backend-conventions} §9）。
 */
public class ProductCustomEntity {

  private String productId;
  private String categoryId;
  private String name;
  private String description;

  public String getProductId() {
    return productId;
  }

  public void setProductId(String productId) {
    this.productId = productId;
  }

  public String getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(String categoryId) {
    this.categoryId = categoryId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
