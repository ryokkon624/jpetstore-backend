package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

/**
 * カート追加/更新の事前チェック用エンティティ（#4・{@code m_item}×{@code t_inventory}×{@code t_cart_item} JOIN）。
 *
 * <p>{@code m_item} に該当行が無ければ mapper は {@code null} を返す（呼び出し元がアイテム不存在＝{@code
 * ResourceNotFoundException}→404 と判定する。AC4・SBD-10）。{@link #stockQuantity}/{@link #currentQuantity}
 * はいずれも {@code CartApplicationService} 内でのみ使う内部値（在庫数非露出・ID-28）。
 *
 * <p>MyBatis Generator の生成対象外（カートはカスタム手書きXMLマッパー。{@code backend-conventions} §9）。
 */
public class ItemForCartCustomEntity {

  private String itemId;
  private int stockQuantity;
  private int currentQuantity;

  public String getItemId() {
    return itemId;
  }

  public void setItemId(String itemId) {
    this.itemId = itemId;
  }

  public int getStockQuantity() {
    return stockQuantity;
  }

  public void setStockQuantity(int stockQuantity) {
    this.stockQuantity = stockQuantity;
  }

  public int getCurrentQuantity() {
    return currentQuantity;
  }

  public void setCurrentQuantity(int currentQuantity) {
    this.currentQuantity = currentQuantity;
  }
}
