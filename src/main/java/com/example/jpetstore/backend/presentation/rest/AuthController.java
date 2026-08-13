package com.example.jpetstore.backend.presentation.rest;

import com.example.jpetstore.backend.application.service.AuthApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認証エンドポイント（AC3）。
 *
 * <p>credential を交換するログイン API（{@code POST /api/auth/login}）は #21 の範囲。本 Story は refresh のみ提供する。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthApplicationService authApplicationService;

  public AuthController(AuthApplicationService authApplicationService) {
    this.authApplicationService = authApplicationService;
  }

  /** refresh token（httpOnly Cookie）のみで access token を再発行する。credential 不要。 */
  @PostMapping("/refresh")
  public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
    authApplicationService.refreshAccessToken(request, response);
    return ResponseEntity.noContent().build();
  }
}
