package com.example.jpetstore.backend.application.service;

import com.example.jpetstore.backend.domain.security.AuthenticatedUser;
import com.example.jpetstore.backend.infrastructure.security.AuthCookieSupport;
import com.example.jpetstore.backend.infrastructure.security.AuthenticatedUserDetails;
import com.example.jpetstore.backend.infrastructure.security.JwtProperties;
import com.example.jpetstore.backend.infrastructure.security.JwtService;
import com.example.jpetstore.backend.infrastructure.security.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

/**
 * 認証まわりのユースケース（AC1/AC2/AC3・#18・#20）。
 *
 * <p>credential（username/password）を交換して初回ログインする処理（access/refresh の初回発行・{@code UserDetailsService} の
 * DB 結線）とログアウトは #18 で追加した（#23 時点では refresh のみの土台だったため、当時の コメントは #21 と誤記していたが実際は #18）。
 *
 * <p>#20 AC1(SBD-6): レート制限/ロックアウトの前段ゲート（{@link LoginAttemptService}）をここに結線する。
 */
@Service
public class AuthApplicationService {

  private final AuthenticationManager authenticationManager;
  private final JwtService jwtService;
  private final AuthCookieSupport cookieSupport;
  private final JwtProperties jwtProperties;
  private final LoginAttemptService loginAttemptService;

  public AuthApplicationService(
      AuthenticationManager authenticationManager,
      JwtService jwtService,
      AuthCookieSupport cookieSupport,
      JwtProperties jwtProperties,
      LoginAttemptService loginAttemptService) {
    this.authenticationManager = authenticationManager;
    this.jwtService = jwtService;
    this.cookieSupport = cookieSupport;
    this.jwtProperties = jwtProperties;
    this.loginAttemptService = loginAttemptService;
  }

  /**
   * username/password を照合し、成功すれば access/refresh を新規発行して httpOnly Cookie にセットする（AC1/AC2）。
   *
   * <p>#20 AC1(SBD-6): {@link LoginAttemptService#assertNotLocked} を authenticate() の前に呼び、ロック中なら
   * 既存の誤資格と同一の例外で短絡する（列挙不可）。authenticate() が失敗した場合は {@link LoginAttemptService#recordFailure}
   * を記録してから同一の例外をそのまま再送出する（呼び出し元の一律401ハンドリングを変えない）。
   *
   * <p>{@link AuthenticationManager#authenticate} が資格情報を照合する（{@code DaoAuthenticationProvider} 経由で
   * {@code JdbcUserDetailsService}＋#19 の {@code PasswordEncoder} を使用）。誤 password・未知 username はいずれも
   * {@code BadCredentialsException}（{@code AuthenticationException} のサブタイプ）として一律に失敗し、{@code
   * GlobalExceptionHandler} が 401 に正規化する（SBD-6・列挙不可）。
   *
   * <p><b>セッション固定化対策（AC2/AC-neg1・SBD-4 の JWT 方式への翻訳）</b>: 呼び出し元が事前に（攻撃者由来等の） ACCESS_TOKEN Cookie
   * を保持していても、ここで発行するのは常に新規署名の access/refresh トークンであり、供給された
   * トークンをそのまま再利用・エコーすることはない。ログイン成功＝新規トークン発行という形で固定化を防止する。
   */
  public AuthenticatedUser login(
      String username, String rawPassword, HttpServletResponse response) {
    loginAttemptService.assertNotLocked(username);

    Authentication authResult;
    try {
      authResult =
          authenticationManager.authenticate(
              new UsernamePasswordAuthenticationToken(username, rawPassword));
    } catch (AuthenticationException e) {
      loginAttemptService.recordFailure(username);
      throw e;
    }
    loginAttemptService.recordSuccess(username);

    AuthenticatedUserDetails principal = (AuthenticatedUserDetails) authResult.getPrincipal();
    AuthenticatedUser user = principal.authenticatedUser();

    String accessToken = jwtService.generateAccessToken(user);
    String refreshToken = jwtService.generateRefreshToken(user);
    cookieSupport.writeAccessTokenCookie(response, accessToken, jwtProperties.accessTokenTtl());
    cookieSupport.writeRefreshTokenCookie(response, refreshToken, jwtProperties.refreshTokenTtl());
    return user;
  }

  /**
   * access/refresh の両 Cookie を即時失効させる（AC2）。
   *
   * <p>access/refresh は自己完結トークンで revocation store を持たないため（{@link #refreshAccessToken} の Javadoc
   * 参照）、他クライアントに漏えい済みのトークン自体を失効させることはできない（既知の制約。将来 revocation store 導入時に強化する）。本メソッドはこのクライアントの
   * Cookie を消去するところまでを担う。
   */
  public void logout(HttpServletResponse response) {
    cookieSupport.clearAuthCookies(response);
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
