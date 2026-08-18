package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

/**
 * ログイン/再水和用のテーマ配色/言語設定参照専用カスタムエンティティ（#36/#25）。
 *
 * <p>MyBatis Generator の生成対象外（{@code m_profile} を単純 SELECT するための手書き Entity。配置・命名規約は {@code
 * backend-conventions} §9 に従う）。{@link AccountEditCustomEntity}（{@code m_account}⋈{@code m_profile}
 * JOIN・version込み）とは別の軽量read-model用（version・氏名/連絡先/住所は保持しない）。
 */
public class AccountPreferencesCustomEntity {

  private String colorSchemePreference;
  private String languagePreference;

  public String getColorSchemePreference() {
    return colorSchemePreference;
  }

  public void setColorSchemePreference(String colorSchemePreference) {
    this.colorSchemePreference = colorSchemePreference;
  }

  public String getLanguagePreference() {
    return languagePreference;
  }

  public void setLanguagePreference(String languagePreference) {
    this.languagePreference = languagePreference;
  }
}
