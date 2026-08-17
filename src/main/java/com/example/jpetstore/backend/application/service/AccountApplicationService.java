package com.example.jpetstore.backend.application.service;

import com.example.jpetstore.backend.domain.account.AccountContact;
import com.example.jpetstore.backend.domain.account.AccountRepository;
import com.example.jpetstore.backend.domain.exception.ResourceNotFoundException;
import com.example.jpetstore.backend.domain.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * プリフィル用の自分自身の氏名/連絡先/住所を返すユースケース（#7・計画フェーズ確定①）。
 *
 * <p>対象は常に呼び出し元（{@link CurrentUserProvider}）自身の {@code m_account} 行のみ。URL/リクエストにuserId等を取らないため
 * IDOR面はゼロ（{@code CartApplicationService} と同じ思想）。SELECT のみに厳格限定し、更新系メソッドは持たない（E4/F4.2 編集側・#8
 * 送信/在庫を先取りしない）。
 *
 * <p>#30: {@code AccountContactCustomMapper} 直呼びから {@link AccountRepository}（Domain層）経由へ retrofit
 * した（{@code backend-conventions} §9）。
 */
@Service
public class AccountApplicationService {

  private final AccountRepository accountRepository;
  private final CurrentUserProvider currentUserProvider;

  public AccountApplicationService(
      AccountRepository accountRepository, CurrentUserProvider currentUserProvider) {
    this.accountRepository = accountRepository;
    this.currentUserProvider = currentUserProvider;
  }

  /**
   * 現在の認証プリンシパル自身の氏名/連絡先/住所を返す。
   *
   * @throws org.springframework.security.access.AccessDeniedException 未認証（{@code
   *     CurrentUserProvider#requireCurrentUser} の既定動作）
   * @throws ResourceNotFoundException 認証プリンシパルに対応する {@code m_account} 行が存在しない場合
   */
  @Transactional(readOnly = true)
  public AccountContact getMyContact() {
    Long userId = currentUserProvider.requireCurrentUser().userId();
    return accountRepository
        .findContactByUserId(userId)
        .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
  }
}
