package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

/**
 * {@code t_cart}（カートヘッダ）1件を表す参照/書き込み用エンティティ（#4）。
 *
 * <p>{@link #cartId} は {@code ensureCart}（upsert-get-id）実行後に MyBatis の生成キーで補完される。 {@link
 * #createUserId}/{@link #updateUserId} は {@code AuditProgramInterceptor} の自動補完対象外のため、呼び出し元（{@code
 * CartApplicationService}）が {@code CurrentUserProvider} から明示的に設定する。{@link #createProgram}/{@link
 * #updateProgram} は同インターセプタが 自動補完する（未設定のままでよい）。
 *
 * <p>MyBatis Generator の生成対象外（{@code backend-conventions} §9・カスタム手書きXMLマッパー）。
 */
public class CartHeaderCustomEntity {

  private Long cartId;
  private Long userId;
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

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
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
