package com.example.jpetstore.backend.domain.account;

/**
 * 編集プリフィル用の自分自身のアカウント/プロフィール詳細を表す参照系ドメインモデル（#14 AC3・E3）。
 *
 * <p>{@link AccountContact}（チェックアウト用・version非保持）とは別の read-model。編集フォームが
 * 必要とする全編集可フィールド（氏名/連絡先/住所/langpref/favcategory）に加え、楽観ロックの往復に 必要な {@link #version}
 * を持つ。username/status/WHO列は編集画面に出さない（内部項目・PO決定）。
 */
public record AccountEditDetail(
    String firstName,
    String lastName,
    String email,
    String phone,
    String address1,
    String address2,
    String city,
    String state,
    String postalCode,
    String country,
    String languagePreference,
    String favoriteCategoryId,
    long version) {}
