package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

/**
 * {@code m_category} 1件を表す参照専用エンティティ（#1）。
 *
 * <p>MyBatis Generator の生成対象外（カタログはカスタム手書きXMLマッパー。{@code backend-conventions} §9）。
 */
public class CategoryCustomEntity {

  private String categoryId;
  private String name;
  private String description;

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
