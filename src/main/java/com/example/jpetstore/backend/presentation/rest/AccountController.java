package com.example.jpetstore.backend.presentation.rest;

import com.example.jpetstore.backend.application.service.AccountApplicationService;
import com.example.jpetstore.backend.domain.account.AccountContact;
import com.example.jpetstore.backend.domain.account.AccountEditCommand;
import com.example.jpetstore.backend.domain.account.AccountEditDetail;
import com.example.jpetstore.backend.domain.account.PasswordChangeCommand;
import com.example.jpetstore.backend.presentation.rest.validation.StrongPassword;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * アカウント/プロフィールAPI（#7 read-only・#14 編集・#15 PW変更）。
 *
 * <p>既存の {@code GET /api/auth/me}（username/roles）とは別物・パス衝突なし。{@code SecurityConfig} は無変更（{@code
 * /api/account/**} は既存の {@code anyRequest().authenticated()} 配下＝未認証は自動的に401。permitAll には追加しない）。
 *
 * <p>{@link #me()}（{@code GET /api/account/me}）はチェックアウトのプリフィル専用でSELECTのみに厳格限定する
 * （E4/F4.2編集側・#8送信/在庫を先取りしない）。{@link #getAccount()}/{@link #updateAccount}（{@code GET}/{@code PUT
 * /api/account}）が#14の編集用エンドポイント（E3：{@code /me}とは別の新規パス・{@code /me}は 無変更）。version楽観ロック（arch
 * §4.2）・本人固定（AC1・{@code CurrentUserProvider}起点でURLにuserIdを 取らないためIDOR面はゼロ）・allowlist（AC2）は{@code
 * AccountApplicationService}側で担保する。
 *
 * <p><b>#15/#16: {@link #changePassword}のステータス設計（計画フェーズ確定・三系統分離）</b>: 現在パスワード誤り＝{@code
 * InvalidCurrentPasswordException}経由で422（401は{@code httpClient}のsilent refreshを誤発火させ、403はCSRF欠落と
 * 衝突するため回避）／弱いパスワード・不正入力（Bean Validation）＝400／真の未認証＝401（既存の{@code
 * AuthenticationEntryPoint}経路）／CSRF欠落＝403。CSRF自体は{@code SecurityConfig}の既存機構（{@code
 * CookieCsrfTokenRepository}のSameSite=Strict + 非XORのcookie-to-header double-submit）がこのエンドポイントにも
 * 無改造で適用される（{@code /api/account/**}が{@code csrf()}設定配下のため）。新規のOrigin/Refererフィルタは追加しない （Sprint9
 * #6・{@code SecurityConfig}既存コメントと同方針）。{@link #changePassword}成功時は{@code
 * AuthApplicationService#issueTokensFor}で現在セッションのトークンのみをローテートする（Q3。全セッション即時失効は対象外）。{@code
 * m_signon}はversion楽観ロック対象外（現在PW再認証ゲート自体が競合防止を担保する・{@code m_account.version}アグリゲートに含めない）。
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

  private final AccountApplicationService accountApplicationService;

  public AccountController(AccountApplicationService accountApplicationService) {
    this.accountApplicationService = accountApplicationService;
  }

  /** 現在の認証プリンシパル自身の氏名/連絡先/住所を返す。他人のidentityは引けない（列挙防止）。 */
  @GetMapping("/me")
  public AccountResponse me() {
    return AccountResponse.from(accountApplicationService.getMyContact());
  }

  /** 編集プリフィル用に現在の認証プリンシパル自身の全編集可フィールドとversionを返す（#14 AC3・E3）。 */
  @GetMapping
  public AccountEditResponse getAccount() {
    return AccountEditResponse.from(accountApplicationService.getAccountForEdit());
  }

  /** 現在の認証プリンシパル自身のアカウント/プロフィールを更新する（#14 AC1〜AC3・非冪等PUT・CSRF前提）。 */
  @PutMapping
  public AccountEditResponse updateAccount(@Valid @RequestBody AccountEditRequest request) {
    return AccountEditResponse.from(accountApplicationService.updateAccount(request.toCommand()));
  }

  /**
   * 現在の認証プリンシパル自身のパスワードを変更する（#15 AC1〜AC2・非冪等POST・CSRF前提）。
   *
   * <p>現在パスワード誤り（{@code InvalidCurrentPasswordException}）は422、弱いパスワード/不正入力（Bean
   * Validation）は400、真の未認証は401、CSRF欠落は403（{@code GlobalExceptionHandler}が正規化・計画フェーズ確定）。 成功時は{@code
   * AuthApplicationService#issueTokensFor}で現在セッションのトークンをローテートする（Q3）。
   */
  @PostMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(
      @Valid @RequestBody PasswordChangeRequest request, HttpServletResponse response) {
    accountApplicationService.changePassword(request.toCommand(), response);
  }

  /** 住所/氏名応答DTO（Controller層に閉じる）。username/status/version/WHO列・カード列は含まない（read-onlyに厳格限定）。 */
  public record AccountResponse(
      String firstName,
      String lastName,
      String email,
      String phone,
      String address1,
      String address2,
      String city,
      String state,
      String postalCode,
      String country) {
    static AccountResponse from(AccountContact contact) {
      return new AccountResponse(
          contact.firstName(),
          contact.lastName(),
          contact.email(),
          contact.phone(),
          contact.address1(),
          contact.address2(),
          contact.city(),
          contact.state(),
          contact.postalCode(),
          contact.country());
    }
  }

  /**
   * 編集リクエストDTO（Controller層に閉じる・SBD-2アローリスト）。userid/username/status/WHO列は持たない （サーバ権威）。{@link
   * #version} はGET時に返した値をクライアントが往復させたもの（arch §4.2の読込トークン）。#17 AC1（Q2確定）: email形式・各項目の 最大長は{@code
   * m_account}/{@code m_profile}のDBカラム幅に整合させる。
   */
  public record AccountEditRequest(
      long version,
      @NotBlank @Size(max = 80) String firstName,
      @NotBlank @Size(max = 80) String lastName,
      @NotBlank @Email @Size(max = 80) String email,
      @NotBlank @Size(max = 80) String phone,
      @NotBlank @Size(max = 80) String address1,
      @Size(max = 40) String address2,
      @NotBlank @Size(max = 80) String city,
      @NotBlank @Size(max = 80) String state,
      @NotBlank @Size(max = 20) String postalCode,
      @NotBlank @Size(max = 20) String country,
      @NotBlank @Size(max = 80) String languagePreference,
      @Size(max = 10) String favoriteCategoryId,
      @NotBlank @Pattern(regexp = "^(system|light|dark)$") @Size(max = 20)
          String colorSchemePreference) {

    AccountEditCommand toCommand() {
      return new AccountEditCommand(
          version,
          firstName,
          lastName,
          email,
          phone,
          address1,
          address2,
          city,
          state,
          postalCode,
          country,
          languagePreference,
          favoriteCategoryId,
          colorSchemePreference);
    }
  }

  /** 編集応答DTO（Controller層に閉じる）。username/status/WHO列は含まない。versionは更新後の最新値（往復用）。 */
  public record AccountEditResponse(
      String firstName,
      String lastName,
      String email,
      String phone,
      String address1,
      String address2,
      String city,
      String state,
      String postalCode,
      String country,
      String languagePreference,
      String favoriteCategoryId,
      String colorSchemePreference,
      long version) {
    static AccountEditResponse from(AccountEditDetail detail) {
      return new AccountEditResponse(
          detail.firstName(),
          detail.lastName(),
          detail.email(),
          detail.phone(),
          detail.address1(),
          detail.address2(),
          detail.city(),
          detail.state(),
          detail.postalCode(),
          detail.country(),
          detail.languagePreference(),
          detail.favoriteCategoryId(),
          detail.colorSchemePreference(),
          detail.version());
    }
  }

  /**
   * PW変更リクエストDTO（Controller層に閉じる・SBD-2アローリスト・#15）。username/userIdは持たない（{@code
   * CurrentUserProvider}起点でサーバ側が解決＝本人固定）。{@link #newPassword}は{@link StrongPassword}（#17と共有の1本化制約）。
   */
  public record PasswordChangeRequest(
      @NotBlank String currentPassword, @StrongPassword String newPassword) {

    PasswordChangeCommand toCommand() {
      return new PasswordChangeCommand(currentPassword, newPassword);
    }
  }
}
