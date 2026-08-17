package com.example.jpetstore.backend.domain.account;

/**
 * アカウント/プロフィール編集ユースケースの入力コマンド（#14 AC1〜AC3・計画フェーズ確定）。
 *
 * <p>allowlist（AC2/SBD-2）: 氏名/連絡先/住所/langpref/favcategoryのみ。userid/username/status/version/WHO
 * 列はクライアントから一切受け取らない（サーバ権威。{@link #userId} は{@code CurrentUserProvider}起点で
 * サーバ側が解決した値・AC1のため実質「本人固定」の担保そのもの）。{@link #expectedVersion} はGET時に返した {@code
 * version}をクライアントが往復させたもの（楽観ロックの読込トークン・arch §4.2）。
 */
public record AccountUpdate(
    Long userId,
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
