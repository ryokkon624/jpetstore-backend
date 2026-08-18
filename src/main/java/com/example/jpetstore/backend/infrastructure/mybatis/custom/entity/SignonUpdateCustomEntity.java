package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

/**
 * パスワード変更の{@code m_signon} UPDATE用パラメータエンティティ（#15）。
 *
 * <p>{@code m_signon}はversion楽観ロック対象外（PW変更は現在PW再認証ゲートで担保・{@code m_account.version}
 * アグリゲートに含めない・計画フェーズ確定）。{@link #updateProgram}は{@code AuditProgramInterceptor}が自動補完する （{@code
 * AccountUpdateCustomEntity}と同じくフィールドは持つが明示設定はしない）。POJO（Map未経由）であるため
 * インターセプタのMap起因の意図しないput混入を回避する（{@code backend-conventions}の既知の罠を踏まない構成）。
 *
 * <p>MyBatis Generator の生成対象外（{@code backend-conventions} §9・カスタムアノテーションマッパー）。
 */
public class SignonUpdateCustomEntity {

  private Long userId;
  private String passwordHash;
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
