package com.example.jpetstore.backend.domain.account;

/**
 * ユーザー登録に必要な最小の書込み用ドメインモデル（#13）。
 *
 * <p>{@link AccountRepository#register} への入力を表す薄いVO（{@code NewOrder}・O1=案A と同じ思想）。{@link
 * #passwordHash} は呼び出し元（{@code RegistrationApplicationService}）が {@code PasswordEncoder} で
 * bcryptエンコード済みの値を渡す（AC3・SBD-5・平文パスワードはここに含まれない）。{@link #languagePreference} は サーバ既定値（未指定時
 * "english"・E5）を呼び出し元が解決済みの値を渡す。{@link #favoriteCategoryId} は任意（NULL可）。
 *
 * <p>{@code username}/{@code status}/{@code version}/WHO列はクライアント入力を受けず、サーバ側で決定・自動付与する
 * （status="OK"はRepository実装が付与・E7。version=0はDB既定値・WHOはInterceptor/明示NULLで付与）。
 */
public record NewAccountRegistration(
    String username,
    String passwordHash,
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
