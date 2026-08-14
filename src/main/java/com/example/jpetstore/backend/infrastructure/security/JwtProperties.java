package com.example.jpetstore.backend.infrastructure.security;

import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 署名鍵・トークン有効期間を保持する設定クラス。
 *
 * <p>AC5/AC-neg1 (SBD-11): {@code jwt.secret}（環境変数 {@code JWT_SECRET}）はデフォルト値を持たない。 未設定時は Spring
 * のプレースホルダ解決自体が失敗し起動不可（fail-fast）。設定されていても HS256 の 最小鍵長（256bit=32byte）未満なら起動時に例外を投げる。
 */
@Component
public class JwtProperties {

  private static final int MIN_SECRET_BYTES = 32; // HS256 = 256bit

  private final SecretKey signingKey;
  private final Duration accessTokenTtl;
  private final Duration refreshTokenTtl;

  public JwtProperties(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-token-ttl}") Duration accessTokenTtl,
      @Value("${jwt.refresh-token-ttl}") Duration refreshTokenTtl) {
    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "jwt.secret (JWT_SECRET) must be at least "
              + MIN_SECRET_BYTES
              + " bytes for HS256 signing (actual: "
              + keyBytes.length
              + " bytes)");
    }
    this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    this.accessTokenTtl = accessTokenTtl;
    this.refreshTokenTtl = refreshTokenTtl;
  }

  public SecretKey signingKey() {
    return signingKey;
  }

  public Duration accessTokenTtl() {
    return accessTokenTtl;
  }

  public Duration refreshTokenTtl() {
    return refreshTokenTtl;
  }
}
