package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

/**
 * {@code t_inventory} のガード付きアトミック減算（#8 AC2・architecture-conventions §4.1）のパラメータエンティティ。
 *
 * <p>UPDATE 専用（INSERT を伴わない）ため {@code createUserId}/{@code createProgram} は持たない。{@link #quantity}
 * は減算する量（カート内数量）。呼び出し元の {@code UPDATE ... WHERE item_id = :id AND quantity >= :quantity} が affected
 * rows で在庫充足を判定する（{@code SELECT ... FOR UPDATE}・{@code version} 列は使わない。本UPDATE文自体の行ロックで直列化する）。
 */
public class InventoryDecreaseCustomEntity {

  private String itemId;
  private int quantity;
  private Long updateUserId;
  private String updateProgram;

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
