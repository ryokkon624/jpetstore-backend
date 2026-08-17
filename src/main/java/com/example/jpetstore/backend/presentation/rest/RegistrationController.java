package com.example.jpetstore.backend.presentation.rest;

import com.example.jpetstore.backend.application.service.RegistrationApplicationService;
import com.example.jpetstore.backend.domain.account.RegisterAccountCommand;
import com.example.jpetstore.backend.domain.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * ユーザー登録エンドポイント（#13 AC1〜AC6・[L2]・AC-neg1/AC-neg2）。
 *
 * <p>独立パス {@code POST /api/register}（E8）を採用する。{@code /api/account/**} は一律 {@code
 * anyRequest().authenticated()} のまま維持し、メソッド別permitAllの微妙さを回避する（{@code SecurityConfig}参照）。
 * 状態変更のためCSRF前提（非GET）。GETでは受理しない（AC-neg2相当・405）。
 *
 * <p>リクエストはallowlist（SBD-2）: username/password/repeatedPassword/氏名/email/phone/住所のみ。
 * userid/status/version/WHO列はサーバ権威（クライアントから受け取らない）。email形式・PW強度検証は#17委譲 （必須項目非空のみ検証・計画フェーズ確定）。
 */
@RestController
@RequestMapping("/api/register")
public class RegistrationController {

  private final RegistrationApplicationService registrationApplicationService;

  public RegistrationController(RegistrationApplicationService registrationApplicationService) {
    this.registrationApplicationService = registrationApplicationService;
  }

  /** アカウントを登録し、成功すれば自動ログイン（fresh JWT・httpOnly Cookie）した状態で201を返す（AC1/AC2）。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RegistrationResponse register(
      @Valid @RequestBody RegisterRequest request,
      HttpServletRequest servletRequest,
      HttpServletResponse response) {
    AuthenticatedUser user =
        registrationApplicationService.register(request.toCommand(), servletRequest, response);
    return new RegistrationResponse(user.username(), user.roles());
  }

  /**
   * 登録リクエストDTO（Controller層に閉じる・SBD-2アローリスト）。必須項目非空のみ検証する（email形式・PW強度は#17委譲・ 計画フェーズ確定）。{@link
   * #languagePreference}/{@link #favoriteCategoryId} は任意（登録フォームに入力欄なし・E5）。
   */
  public record RegisterRequest(
      @NotBlank String username,
      @NotBlank String password,
      @NotBlank String repeatedPassword,
      @NotBlank String email,
      @NotBlank String firstName,
      @NotBlank String lastName,
      @NotBlank String address1,
      String address2,
      @NotBlank String city,
      @NotBlank String state,
      @NotBlank String postalCode,
      @NotBlank String country,
      @NotBlank String phone,
      String languagePreference,
      String favoriteCategoryId) {

    RegisterAccountCommand toCommand() {
      return new RegisterAccountCommand(
          username,
          password,
          repeatedPassword,
          email,
          firstName,
          lastName,
          address1,
          address2,
          city,
          state,
          postalCode,
          country,
          phone,
          languagePreference,
          favoriteCategoryId);
    }
  }

  /** 登録成功応答DTO（Controller層に閉じる）。httpOnly CookieはJSから読めないため、SPA側の表示用に最小限の識別情報のみ返す。 */
  public record RegistrationResponse(String username, List<String> roles) {}
}
