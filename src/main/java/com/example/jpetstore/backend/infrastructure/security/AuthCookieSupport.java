package com.example.jpetstore.backend.infrastructure.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * JWT を格納する httpOnly Cookie の読み書きヘルパ（AC3・SBD-3/4/15）。
 *
 * <p>Secure/HttpOnly/SameSite/Path/Max-Age を secure-by-default で付与する。SameSite は SPA が same-site
 * で配信できない場合は {@code jwt.cookie.same-site=Lax} に切り替える運用を想定 （README 参照）。
 */
@Component
public class AuthCookieSupport {

  public static final String ACCESS_TOKEN_COOKIE = "ACCESS_TOKEN";
  public static final String REFRESH_TOKEN_COOKIE = "REFRESH_TOKEN";

  private static final String COOKIE_PATH = "/";

  private final boolean secure;
  private final String sameSite;

  public AuthCookieSupport(
      @Value("${jwt.cookie.secure:true}") boolean secure,
      @Value("${jwt.cookie.same-site:Strict}") String sameSite) {
    this.secure = secure;
    this.sameSite = sameSite;
  }

  public void writeAccessTokenCookie(HttpServletResponse response, String token, Duration maxAge) {
    addCookie(response, ACCESS_TOKEN_COOKIE, token, maxAge);
  }

  public void writeRefreshTokenCookie(HttpServletResponse response, String token, Duration maxAge) {
    addCookie(response, REFRESH_TOKEN_COOKIE, token, maxAge);
  }

  /** ログアウト等で両 Cookie を即時失効させる。 */
  public void clearAuthCookies(HttpServletResponse response) {
    addCookie(response, ACCESS_TOKEN_COOKIE, "", Duration.ZERO);
    addCookie(response, REFRESH_TOKEN_COOKIE, "", Duration.ZERO);
  }

  public Optional<String> readCookie(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    // メソッド参照(Cookie::getValue)はnull型安全解析で警告が出るためラムダ化（挙動は不変）。
    return Arrays.stream(cookies)
        .filter(c -> c.getName().equals(name))
        .map(c -> c.getValue())
        .findFirst();
  }

  private void addCookie(HttpServletResponse response, String name, String value, Duration maxAge) {
    ResponseCookie cookie =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .path(COOKIE_PATH)
            .maxAge(maxAge)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }
}
