package com.example.jpetstore.backend.domain.account;

/**
 * ユーザー登録ユースケースの入力コマンド（#13 AC1・計画フェーズ確定）。
 *
 * <p>allowlist（SBD-2）:
 * username/password/repeatedPassword/氏名/email/phone/住所のみ。userid/status/version/WHO
 * 列はクライアントから一切受け取らない（サーバ権威）。{@link #languagePreference}/{@link #favoriteCategoryId} は
 * 任意（登録フォームに入力欄なし・DTOとしてのみ受理・E5）。{@code password}の平文はここに一時的に保持されるが、 {@code
 * RegistrationApplicationService}がbcryptエンコード後は破棄し、永続化されるのはハッシュのみ（AC3・SBD-5）。
 */
public record RegisterAccountCommand(
    String username,
    String password,
    String repeatedPassword,
    String email,
    String firstName,
    String lastName,
    String address1,
    String address2,
    String city,
    String state,
    String postalCode,
    String country,
    String phone,
    String languagePreference,
    String favoriteCategoryId) {}
