package com.example.jpetstore.backend.domain.account;

/**
 * アカウント/プロフィール編集ユースケースのクライアント入力コマンド（#14 AC1〜AC3・計画フェーズ確定）。
 *
 * <p>allowlist（AC2/SBD-2）: 氏名/連絡先/住所/langpref/favcategory/{@link #expectedVersion}のみ。
 * userid/username/status/WHO列は持たない（サーバ権威）。{@code userId}は {@code AccountApplicationService}が{@code
 * CurrentUserProvider}から解決し、{@link AccountUpdate}へ 変換する際に付与する（本人固定・AC1。{@code
 * RegisterAccountCommand}→{@code NewAccountRegistration}と 同じ2段変換パターン）。
 */
public record AccountEditCommand(
    long expectedVersion,
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
    String favoriteCategoryId) {}
