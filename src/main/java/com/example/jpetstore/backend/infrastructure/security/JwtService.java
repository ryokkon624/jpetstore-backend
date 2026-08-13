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
 */
@Component
public class JwtService {

  private static final String CLAIM_USERNAME = "username";
  private static final String CLAIM_ROLES = "roles";

  private final JwtProperties properties;

  public JwtService(JwtProperties properties) {
    this.properties = properties;
  }

  public String generateAccessToken(AuthenticatedUser user) {
    return buildToken(user, properties.accessTokenTtl());
  }

  public String generateRefreshToken(AuthenticatedUser user) {
    return buildToken(user, properties.refreshTokenTtl());
  }

  /** トークンを検証し、成功すれば {@link AuthenticatedUser} を返す。失敗（署名不一致・期限切れ・不正形式）は空。 */
  public Optional<AuthenticatedUser> parseToken(String token) {
    try {
      Claims claims =
          Jwts.parser()
              .verifyWith(properties.signingKey())
              .build()
              .parseSignedClaims(token)
              .getPayload();
      Long userId = Long.valueOf(claims.getSubject());
      String username = claims.get(CLAIM_USERNAME, String.class);
      @SuppressWarnings("unchecked")
      List<String> roles = claims.get(CLAIM_ROLES, List.class);
      return Optional.of(new AuthenticatedUser(userId, username, roles));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private String buildToken(AuthenticatedUser user, Duration ttl) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(String.valueOf(user.userId()))
        .claim(CLAIM_USERNAME, user.username())
        .claim(CLAIM_ROLES, user.roles())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .signWith(properties.signingKey())
        .compact();
  }
}
