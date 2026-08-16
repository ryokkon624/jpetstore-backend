package com.example.jpetstore.backend.presentation.rest;

import com.example.jpetstore.backend.application.service.AccountApplicationService;
import com.example.jpetstore.backend.domain.account.AccountContact;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * プリフィル用の住所/氏名参照API（#7・計画フェーズ確定①）。
 *
 * <p>既存の {@code GET /api/auth/me}（username/roles）とは別物・パス衝突なし。{@code SecurityConfig} は無変更（{@code
 * /api/account/**} は既存の {@code anyRequest().authenticated()} 配下＝未認証は自動的に401。permitAll には追加しない）。GET
 * のみで CSRF 追加設定も不要。
 *
 * <p>SELECT のみに厳格限定する（E4/F4.2 編集側・#8 送信/在庫を先取りしない）。今後 E4 でこの custom entity 上に update
 * を足す場合も、本コントローラには追記せず別エンドポイントとする想定。
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
}
