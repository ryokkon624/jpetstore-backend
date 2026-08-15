package com.example.jpetstore.backend.presentation.rest;

import com.example.jpetstore.backend.application.service.AuthApplicationService;
import com.example.jpetstore.backend.domain.security.AuthenticatedUser;
import com.example.jpetstore.backend.domain.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認証エンドポイント（AC1/AC2/AC3/AC4・#18）。
 *
 * <p>credential を交換するログイン API（{@code POST /api/auth/login}）・ログアウト（{@code POST /api/auth/logout}）は
 * #18 で追加した（#23 時点では refresh のみの土台だったため、当時のコメントは #21 と誤記していたが実際は #18）。
 *
 * <p>login/logout はいずれも状態変更（AC4・SBD-3）のため CSRF 前提の非冪等 POST のみで受理する（{@code SecurityConfig} の CSRF
 * 設定が全体に適用される）。GET では受理しない（AC-neg2）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthApplicationService authApplicationService;
  private final CurrentUserProvider currentUserProvider;

  public AuthController(
      AuthApplicationService authApplicationService, CurrentUserProvider currentUserProvider) {
    this.authApplicationService = authApplicationService;
    this.currentUserProvider = currentUserProvider;
  }

  /**
   * username/password を照合し、成功すれば access/refresh を新規発行して httpOnly Cookie にセットする（AC1/AC2）。
   *
   * <p>失敗時（誤 password・未知 username 問わず）は {@code AuthApplicationService} が投げる {@code
   * AuthenticationException} を {@code GlobalExceptionHandler} が一律 401 に正規化する（SBD-6・列挙不可）。
   */
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
    AuthenticatedUser user =
        authApplicationService.login(request.username(), request.password(), response);
    return ResponseEntity.ok(new LoginResponse(user.username(), user.roles()));
  }

  /** access/refresh の両 Cookie を即時失効させる（AC2）。credential 不要（Cookie の有無に関わらず成功する）。 */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletResponse response) {
    authApplicationService.logout(response);
    return ResponseEntity.noContent().build();
  }

  /** refresh token（httpOnly Cookie）のみで access token を再発行する。credential 不要。 */
  @PostMapping("/refresh")
  public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
    authApplicationService.refreshAccessToken(request, response);
    return ResponseEntity.noContent().build();
  }

  /**
   * 現在の認証プリンシパルを返す（#24 論点①）。httpOnly Cookie はリロード後もブラウザが自動送信されるが、 フロントの Pinia
   * store（username/roles）はリロードで揮発するため、起動時にこのエンドポイントを叩いて identity を再水和する。
   *
   * <p>本エンドポイントは {@code SecurityConfig} の {@code anyRequest().authenticated()} 配下（permitAll
   * に入れていない）ため、未認証リクエストはこのメソッドに到達する前に {@code AuditingAuthenticationEntryPoint} が 401 を返す。GET＝冪等のため
   * CSRF 不要。自分自身の identity のみを返す（他人を引けない＝列挙防止）。
   */
  @GetMapping("/me")
  public ResponseEntity<LoginResponse> me() {
    AuthenticatedUser user = currentUserProvider.requireCurrentUser();
    return ResponseEntity.ok(new LoginResponse(user.username(), user.roles()));
  }

  /** ログイン要求の DTO（Controller 層に閉じる）。資格情報は body のみで受理する（AC-neg2・GET/query 不可）。 */
  public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

  /** ログイン成功時の応答 DTO。httpOnly Cookie は JS から読めないため、SPA 側の表示用に最小限の識別情報のみ返す。 */
  public record LoginResponse(String username, List<String> roles) {}
}
