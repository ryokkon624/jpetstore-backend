package com.example.jpetstore.backend.infrastructure.security;

import com.example.jpetstore.backend.domain.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * JWT の発行・検証（AC3・SBD-3/4/15）。jjwt 0.12 API を使用する（AC6）。
 *
 * <p>access/refresh いずれも自己完結（userId/username/roles をクレームに含む）なトークンとして発行する。 refresh は DB へのユーザ再照会なしに
 * access を再発行できる（revocation store は後続。短命 access＋期限切れ待ちで失効を割り切る方針）。
 *
 * <p><b>レビュー指摘対応（型混同防止）</b>: access/refresh は TTL 以外の構造が同一だと、refresh token を access token
 * として（またはその逆）誤って受理してしまう危険がある（例: refresh を ACCESS_TOKEN Cookie に入れれば保護エンドポイントに直接認証できてしまう）。{@code
 * typ} claim（{@code "access"}/{@code "refresh"}）を発行時に埋め込み、{@link #parseAccessToken}/{@link
 * #parseRefreshToken} で期待する型と 一致するかを検証する。不一致は検証失敗（空）として扱う。
 */
@Component
public class JwtService {

  private static final String CLAIM_USERNAME = "username";
  private static final String CLAIM_ROLES = "roles";
  private static final String CLAIM_TYPE = "typ";
  private static final String TYPE_ACCESS = "access";
  private static final String TYPE_REFRESH = "refresh";

  private final JwtProperties properties;

  public JwtService(JwtProperties properties) {
    this.properties = properties;
  }

  public String generateAccessToken(AuthenticatedUser user) {
    return buildToken(user, properties.accessTokenTtl(), TYPE_ACCESS);
  }

  public String generateRefreshToken(AuthenticatedUser user) {
    return buildToken(user, properties.refreshTokenTtl(), TYPE_REFRESH);
  }

  /** access token を検証し、成功すれば {@link AuthenticatedUser} を返す。refresh token を渡した場合も空。 */
  public Optional<AuthenticatedUser> parseAccessToken(String token) {
    return parseToken(token, TYPE_ACCESS);
  }

  /** refresh token を検証し、成功すれば {@link AuthenticatedUser} を返す。access token を渡した場合も空。 */
  public Optional<AuthenticatedUser> parseRefreshToken(String token) {
    return parseToken(token, TYPE_REFRESH);
  }

  /** トークンを検証し、{@code expectedType} と {@code typ} claim が一致すれば {@link AuthenticatedUser} を返す。 */
  private Optional<AuthenticatedUser> parseToken(String token, String expectedType) {
    try {
      Claims claims =
          Jwts.parser()
              .verifyWith(properties.signingKey())
              .build()
              .parseSignedClaims(token)
              .getPayload();
      String type = claims.get(CLAIM_TYPE, String.class);
      if (!expectedType.equals(type)) {
        return Optional.empty();
      }
      Long userId = Long.valueOf(claims.getSubject());
      String username = claims.get(CLAIM_USERNAME, String.class);
      @SuppressWarnings("unchecked")
      List<String> roles = claims.get(CLAIM_ROLES, List.class);
      return Optional.of(new AuthenticatedUser(userId, username, roles));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private String buildToken(AuthenticatedUser user, Duration ttl, String type) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(String.valueOf(user.userId()))
        .claim(CLAIM_USERNAME, user.username())
        .claim(CLAIM_ROLES, user.roles())
        .claim(CLAIM_TYPE, type)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .signWith(properties.signingKey())
        .compact();
  }
}
