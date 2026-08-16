package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

/**
 * カート明細（{@code t_cart_item}）の書き込み（upsert）用パラメータエンティティ（#4）。
 *
 * <p>{@link #quantity} は呼び出し元（{@code CartApplicationService}）が在庫上限チェック・クランプ・削除判定
 * （数量0以下の分岐）を済ませたうえで渡す最終的な絶対値（加算後の値）。SQL側では単純な upsert のみを行う
 * （D3・last-write-win。単文アトミック更新で書き込みそのものは原子性を保つ）。
 *
 * <p>{@link #createUserId}/{@link #updateUserId} は呼び出し元が {@code CurrentUserProvider} から明示設定する
 * （{@code AuditProgramInterceptor} の自動補完対象外）。{@link #createProgram}/{@link #updateProgram}
 * は同インターセプタが自動補完する。
 */
public class CartItemWriteCustomEntity {

  private Long cartId;
  private String itemId;
  private int quantity;
  private Long createUserId;
  private String createProgram;
  private Long updateUserId;
  private String updateProgram;

  public Long getCartId() {
    return cartId;
  }

  public void setCartId(Long cartId) {
    this.cartId = cartId;
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
