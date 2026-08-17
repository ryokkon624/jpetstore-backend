package com.example.jpetstore.backend.presentation.rest;

import com.example.jpetstore.backend.application.service.AccountApplicationService;
import com.example.jpetstore.backend.domain.account.AccountContact;
import com.example.jpetstore.backend.domain.account.AccountEditCommand;
import com.example.jpetstore.backend.domain.account.AccountEditDetail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * アカウント/プロフィールAPI（#7 read-only・#14 編集）。
 *
 * <p>既存の {@code GET /api/auth/me}（username/roles）とは別物・パス衝突なし。{@code SecurityConfig} は無変更（{@code
 * /api/account/**} は既存の {@code anyRequest().authenticated()} 配下＝未認証は自動的に401。permitAll には追加しない）。
 *
 * <p>{@link #me()}（{@code GET /api/account/me}）はチェックアウトのプリフィル専用でSELECTのみに厳格限定する
 * （E4/F4.2編集側・#8送信/在庫を先取りしない）。{@link #getAccount()}/{@link #updateAccount}（{@code GET}/{@code PUT
 * /api/account}）が#14の編集用エンドポイント（E3：{@code /me}とは別の新規パス・{@code /me}は 無変更）。version楽観ロック（arch
 * §4.2）・本人固定（AC1・{@code CurrentUserProvider}起点でURLにuserIdを 取らないためIDOR面はゼロ）・allowlist（AC2）は{@code
 * AccountApplicationService}側で担保する。
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
   * #version} はGET時に返した値をクライアントが往復させたもの（arch §4.2の読込トークン）。
   */
  public record AccountEditRequest(
      long version,
      @NotBlank String firstName,
      @NotBlank String lastName,
      @NotBlank String email,
      @NotBlank String phone,
      @NotBlank String address1,
      String address2,
      @NotBlank String city,
      @NotBlank String state,
      @NotBlank String postalCode,
      @NotBlank String country,
      @NotBlank String languagePreference,
      String favoriteCategoryId) {

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
          favoriteCategoryId);
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
          detail.version());
    }
  }
}
