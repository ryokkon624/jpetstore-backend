package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

/**
 * ユーザー登録の {@code m_signon} INSERT用パラメータエンティティ（#13）。
 *
 * <p>{@link #passwordHash} は呼び出し元（{@code RegistrationApplicationService}）が {@code PasswordEncoder}
 * でbcryptエンコード済みの値を設定する（AC3・SBD-5・平文パスワードは一切保持しない）。{@link #createUserId}/{@link #updateUserId}
 * は未認証guestによる登録のため常にNULL（E7・{@code CartHeaderCustomEntity} と同じ規約で明示設定）。
 *
 * <p>MyBatis Generator の生成対象外（{@code backend-conventions} §9・カスタム手書きXMLマッパー）。
 */
public class SignonRegistrationCustomEntity {

  private Long userId;
  private String passwordHash;
  private Long createUserId;
  private String createProgram;
  private Long updateUserId;
  private String updateProgram;

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
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
