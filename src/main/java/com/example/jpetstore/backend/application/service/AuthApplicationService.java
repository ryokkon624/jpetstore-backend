package com.example.jpetstore.backend.application.service;

import com.example.jpetstore.backend.domain.security.AuthenticatedUser;
import com.example.jpetstore.backend.infrastructure.security.AuthCookieSupport;
import com.example.jpetstore.backend.infrastructure.security.JwtProperties;
import com.example.jpetstore.backend.infrastructure.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;

/**
 * 認証まわりのユースケース（AC3）。
 *
 * <p>本 Story のスコープは refresh 機構のみ。credential（username/password）を交換して初回ログイン する処理（access/refresh
 * の初回発行・{@code UserDetailsService} の DB 結線）は #21 の範囲。
 */
@Service
public class AuthApplicationService {

  private final JwtService jwtService;
  private final AuthCookieSupport cookieSupport;
  private final JwtProperties jwtProperties;

  public AuthApplicationService(
      JwtService jwtService, AuthCookieSupport cookieSupport, JwtProperties jwtProperties) {
    this.jwtService = jwtService;
    this.cookieSupport = cookieSupport;
    this.jwtProperties = jwtProperties;
  }

  /**
   * refresh token（httpOnly Cookie）のみで access token を再発行する。credential は不要。
   *
   * <p>refresh token は自己完結（署名検証のみ）で失効管理を行わない（revocation store は後続。 短命 access
   * ＋期限切れ待ちで失効を割り切る方針）。refresh token 自体はローテーションしない （呼び出しごとに再発行しても、失効の仕組みが無い現状ではセキュリティ上の利得が薄いため）。
   */
  public void refreshAccessToken(HttpServletRequest request, HttpServletResponse response) {
    String refreshToken =
        cookieSupport
            .readCookie(request, AuthCookieSupport.REFRESH_TOKEN_COOKIE)
            .orElseThrow(() -> new InsufficientAuthenticationException("Refresh token is missing"));

    AuthenticatedUser user =
        jwtService
            .parseRefreshToken(refreshToken)
            .orElseThrow(
                () ->
                    new InsufficientAuthenticationException("Refresh token is invalid or expired"));

    String newAccessToken = jwtService.generateAccessToken(user);
    cookieSupport.writeAccessTokenCookie(response, newAccessToken, jwtProperties.accessTokenTtl());
  }
}
