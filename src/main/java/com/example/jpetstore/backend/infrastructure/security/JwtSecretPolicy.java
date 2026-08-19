package com.example.jpetstore.backend.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * JWT 署名鍵（{@code jwt.secret}）の起動時 fail-fast 検証ロジック（#38 AC1/AC2/AC3）。
 *
 * <p>{@link JwtProperties} から呼び出される。DB に依存しない純粋な検証ロジックのため、高速な単体テスト（{@code
 * JwtSecretPolicySpec}）で境界値を網羅できるようこのクラスへ分離した。
 *
 * <p>検証順序: (1) 既知 placeholder/弱いリテラルの denylist（主） → (2) 最小鍵長（{@value #MIN_SECRET_BYTES}byte） → (3)
 * 最小エントロピー（ユニーク文字数{@value #MIN_UNIQUE_CHARS}以上・補助）。denylist を最初に置くことで、短い placeholder（例: {@code
 * changeme}）でも「鍵長不足」ではなく対処が明確な placeholder 専用メッセージになる。
 *
 * <p>出典: Phase 4 L3 セキュリティ回帰 N1（{@code reports/after/l3-security-regression-backend.md} §2.1・SEC
 * run {@code security/20260819_01}）。
 */
final class JwtSecretPolicy {

  static final int MIN_SECRET_BYTES = 32; // HS256 = 256bit
  static final int MIN_UNIQUE_CHARS = 24;

  /**
   * 既知の placeholder / 弱いリテラルの denylist（前後空白除去 + 大文字小文字非依存の完全一致）。
   *
   * <p>{@code please-replace-with-a-random-secret-of-at-least-32-bytes} は {@code .env.example}
   * が実際に配布している値のため恒久的にこの表へ残すこと（将来 {@code .env.example} の値を変更しても、過去に配布した値を denylist から削除しない。既存の
   * {@code .env} コピーが黙って残り続けるため）。
   */
  private static final Set<String> DENYLIST =
      Set.of(
          "please-replace-with-a-random-secret-of-at-least-32-bytes",
          "changeme",
          "change-me",
          "changeit",
          "change-it",
          "changethis",
          "replaceme",
          "replace-me",
          "secret",
          "mysecret",
          "supersecret",
          "topsecret",
          "jwtsecret",
          "jwt-secret",
          "jwt_secret",
          "your-secret-key",
          "my-secret-key",
          "password",
          "passw0rd",
          "default",
          "example",
          "placeholder",
          "dummy",
          "todo",
          "test",
          "testing",
          "dev",
          "development");

  private JwtSecretPolicy() {}

  /**
   * {@code secret} を検証する。違反時は {@link IllegalStateException}（fail-fast）を投げる。
   * 例外メッセージには秘密の値そのものを含めない（AC3）。
   */
  static void validate(String secret) {
    String normalized = secret == null ? "" : secret.trim().toLowerCase(Locale.ROOT);
    if (DENYLIST.contains(normalized)) {
      throw new IllegalStateException(
          "jwt.secret (JWT_SECRET) is a known placeholder/weak value and must not be used. "
              + "Generate a fresh secret, e.g. \"openssl rand -base64 32\", and set JWT_SECRET "
              + "in your .env (never commit it).");
    }

    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "jwt.secret (JWT_SECRET) must be at least "
              + MIN_SECRET_BYTES
              + " bytes for HS256 signing (actual: "
              + keyBytes.length
              + " bytes)");
    }

    long uniqueChars = secret.codePoints().distinct().count();
    if (uniqueChars < MIN_UNIQUE_CHARS) {
      throw new IllegalStateException(
          "jwt.secret (JWT_SECRET) has too few distinct characters ("
              + uniqueChars
              + ", minimum "
              + MIN_UNIQUE_CHARS
              + ") and does not look random. Generate a fresh secret, e.g. "
              + "\"openssl rand -base64 32\", and set JWT_SECRET in your .env (never commit it).");
    }
  }
}
