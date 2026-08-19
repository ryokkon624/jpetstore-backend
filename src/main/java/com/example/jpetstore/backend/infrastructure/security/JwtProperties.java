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
 * のプレースホルダ解決自体が失敗し起動不可（fail-fast）。設定されていても {@link JwtSecretPolicy} の検証（既知 placeholder/弱いリテラルの
 * denylist・最小鍵長・最小エントロピー。#38 AC1/AC2）を満たさなければ起動時に例外を投げる。
 */
@Component
public class JwtProperties {

  private final SecretKey signingKey;
  private final Duration accessTokenTtl;
  private final Duration refreshTokenTtl;

  public JwtProperties(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-token-ttl}") Duration accessTokenTtl,
      @Value("${jwt.refresh-token-ttl}") Duration refreshTokenTtl) {
    JwtSecretPolicy.validate(secret);
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
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
