package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

/**
 * ユーザー登録の {@code m_profile} INSERT用パラメータエンティティ（#13）。
 *
 * <p>{@link #languagePreference} は呼び出し元（{@code RegistrationApplicationService}）がサーバ既定値（未指定時
 * "english"・E5）を解決済みの値を設定する。{@link #favoriteCategoryId} は任意（NULL可・登録フォームに入力欄なし）。 {@link
 * #createUserId}/{@link #updateUserId} は未認証guestによる登録のため常にNULL（E7）。
 *
 * <p>MyBatis Generator の生成対象外（{@code backend-conventions} §9・カスタム手書きXMLマッパー）。
 */
public class ProfileRegistrationCustomEntity {

  private Long userId;
  private String languagePreference;
  private String favoriteCategoryId;
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
