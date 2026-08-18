package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

/**
 * アカウント編集の {@code m_profile} UPDATE用パラメータエンティティ（#14 AC1〜AC3・E2）。
 *
 * <p>{@code m_account.version}が編集アグリゲート単一の楽観ロックトークンのため、本UPDATEは無ガード （{@code WHERE
 * user_id=:id}のみ・version比較なし）。{@code m_account}側UPDATEが競合した場合、呼び出し元 （{@code
 * MyBatisAccountRepository#updateAccount}）は本エンティティのUPDATE自体を発行しない。
 *
 * <p>MyBatis Generator の生成対象外（{@code backend-conventions} §9・カスタム手書きXMLマッパー）。
 */
public class ProfileUpdateCustomEntity {

  private Long userId;
  private String languagePreference;
  private String favoriteCategoryId;
  private String colorSchemePreference;
  private Long updateUserId;
  private String updateProgram;

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getLanguagePreference() {
    return languagePreference;
  }

  public void setLanguagePreference(String languagePreference) {
    this.languagePreference = languagePreference;
  }

  public String getFavoriteCategoryId() {
    return favoriteCategoryId;
  }

  public void setFavoriteCategoryId(String favoriteCategoryId) {
    this.favoriteCategoryId = favoriteCategoryId;
  }

  public String getColorSchemePreference() {
    return colorSchemePreference;
  }

  public void setColorSchemePreference(String colorSchemePreference) {
    this.colorSchemePreference = colorSchemePreference;
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
