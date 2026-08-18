package com.example.jpetstore.backend.domain.account;

/**
 * パスワード変更ユースケースのクライアント入力コマンド（#15 AC1〜AC2・計画フェーズ確定）。
 *
 * <p>allowlist（SBD-2）: {@link #currentPassword}/{@link #newPassword}のみ。userid/usernameは持たない （{@code
 * AccountApplicationService}が{@code CurrentUserProvider}から解決する・本人固定・AC1と同じ2段変換 パターン）。{@link
 * #newPassword}の強度検証は{@code PasswordChangeRequest}側の{@code @StrongPassword}が担う （#17
 * register.passwordと共有の1本化制約）。
 */
public record PasswordChangeCommand(String currentPassword, String newPassword) {}
