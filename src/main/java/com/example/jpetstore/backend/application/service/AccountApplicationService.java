package com.example.jpetstore.backend.application.service;

import com.example.jpetstore.backend.domain.account.AccountContact;
import com.example.jpetstore.backend.domain.account.AccountEditCommand;
import com.example.jpetstore.backend.domain.account.AccountEditDetail;
import com.example.jpetstore.backend.domain.account.AccountRepository;
import com.example.jpetstore.backend.domain.account.AccountUpdate;
import com.example.jpetstore.backend.domain.account.PasswordChangeCommand;
import com.example.jpetstore.backend.domain.account.UserPreferences;
import com.example.jpetstore.backend.domain.concurrency.AffectedRows;
import com.example.jpetstore.backend.domain.exception.InvalidCurrentPasswordException;
import com.example.jpetstore.backend.domain.exception.ResourceNotFoundException;
import com.example.jpetstore.backend.domain.security.AuthenticatedUser;
import com.example.jpetstore.backend.domain.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 自分自身のアカウント/プロフィールを扱うユースケース（#7 read-only・#14 編集・#36/#25 設定再水和）。
 *
 * <p>対象は常に呼び出し元（{@link CurrentUserProvider}）自身の {@code m_account}/{@code m_profile} 行のみ。
 * URL/リクエストにuserId等を取らないためIDOR面はゼロ（{@code CartApplicationService} と同じ思想。#14 AC-neg1・{@code
 * OwnershipAuthorizationService}は不要＝本人行のみ触るため所有者照合が構造的に自明）。
 *
 * <p>#30: {@code AccountContactCustomMapper} 直呼びから {@link AccountRepository}（Domain層）経由へ retrofit
 * した（{@code backend-conventions} §9）。
 *
 * <p>#14: {@link #getAccountForEdit}/{@link #updateAccount} は編集プリフィル/version楽観ロック更新 （arch
 * §4.2・コードベース初の実UPDATE）。{@link AccountUpdate#userId()} は{@link
 * CurrentUserProvider}起点でサーバ側が解決した値のみを使う（クライアントの{@code username}等は
 * 一切受け取らない・AC1）。userid/status/version/WHO列はサーバ権威のため{@link AccountEditCommand}に
 * フィールド自体を持たない（AC-neg2・型で構造的に担保）。
 *
 * <p>#36/#25: {@link #getPreferences(Long)} のみ他メソッドと異なり {@link CurrentUserProvider} を使わず引数で明示的に
 * {@code userId} を受け取る（{@code AuthController#login} 実行中は {@code SecurityContext}
 * が未populatedのため使用不可・stateless JWTの制約）。
 */
@Service
public class AccountApplicationService {

  /**
   * {@link #getPreferences} が {@code m_profile} 行を見つけられなかった場合（JWTのuserIdに対応する行が存在しない・
   * テスト用に発行されたJWT等）のフォールバック既定値。{@code RegistrationApplicationService}の登録時既定値 （{@code
   * english}）・{@code V00_000_013}の{@code color_scheme_preference}列既定値（{@code system}）と同値。
   */
  private static final String DEFAULT_COLOR_SCHEME_PREFERENCE = "system";

  private static final String DEFAULT_LANGUAGE_PREFERENCE = "english";

  private final AccountRepository accountRepository;
  private final CurrentUserProvider currentUserProvider;
  private final PasswordEncoder passwordEncoder;
  private final AuthApplicationService authApplicationService;

  public AccountApplicationService(
      AccountRepository accountRepository,
      CurrentUserProvider currentUserProvider,
      PasswordEncoder passwordEncoder,
      AuthApplicationService authApplicationService) {
    this.accountRepository = accountRepository;
    this.currentUserProvider = currentUserProvider;
    this.passwordEncoder = passwordEncoder;
    this.authApplicationService = authApplicationService;
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

  /**
   * 編集プリフィル用に現在の認証プリンシパル自身の全編集可フィールドとversionを返す（#14 AC3・E3）。
   *
   * @throws org.springframework.security.access.AccessDeniedException 未認証
   * @throws ResourceNotFoundException 認証プリンシパルに対応する行が存在しない場合
   */
  @Transactional(readOnly = true)
  public AccountEditDetail getAccountForEdit() {
    Long userId = currentUserProvider.requireCurrentUser().userId();
    return accountRepository
        .findEditDetailByUserId(userId)
        .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
  }

  /**
   * 指定 {@code userId} のテーマ配色/言語設定を返す（#36/#25）。
   *
   * <p>{@code /api/auth/login}/{@code /api/auth/me} 共通の再水和用。{@link #getMyContact}/{@link
   * #getAccountForEdit} と異なり {@link CurrentUserProvider} には依存せず、呼び出し元が解決した {@code userId} を
   * 明示的に受け取る（login()実行中は{@code SecurityContext}が未populatedのため{@code
   * CurrentUserProvider}が使えない・stateless JWTの制約）。{@code m_profile}行が見つからない場合（テスト用に
   * 発行されたJWT等）は例外を投げず既定値へフォールバックする（{@code AuthMeSpec}等の既存契約を壊さない）。
   */
  @Transactional(readOnly = true)
  public UserPreferences getPreferences(Long userId) {
    return accountRepository
        .findPreferencesByUserId(userId)
        .orElseGet(
            () ->
                new UserPreferences(DEFAULT_COLOR_SCHEME_PREFERENCE, DEFAULT_LANGUAGE_PREFERENCE));
  }

  /**
   * 現在の認証プリンシパル自身のアカウント/プロフィールを更新する（#14 AC1〜AC3）。
   *
   * <p>更新後の {@link AccountEditDetail} は再読取り（追加SELECT）せず、{@code command} が持つ入力値と{@code
   * expectedVersion+1}から直接組み立てて返す（成功した時点で永続化された値は{@code
   * command}の入力値そのものであるため。Sprint12/13の教訓＝識別子解決用と最終応答用の読取を同一の集約全体
   * 読み込みで済ませない、をさらに進め「そもそも再読取りしない」設計とした）。
   *
   * @throws org.springframework.security.access.AccessDeniedException 未認証
   * @throws com.example.jpetstore.backend.domain.exception.OptimisticLockConflictException {@code
   *     expectedVersion} が最新でない場合（affected rows==0・409）
   */
  @Transactional
  public AccountEditDetail updateAccount(AccountEditCommand command) {
    Long userId = currentUserProvider.requireCurrentUser().userId();
    AccountUpdate update =
        new AccountUpdate(
            userId,
            command.expectedVersion(),
            command.firstName(),
            command.lastName(),
            command.email(),
            command.phone(),
            command.address1(),
            command.address2(),
            command.city(),
            command.state(),
            command.postalCode(),
            command.country(),
            command.languagePreference(),
            command.favoriteCategoryId(),
            command.colorSchemePreference());

    int affected = accountRepository.updateAccount(update);
    AffectedRows.requireUpdated(affected);

    return new AccountEditDetail(
        command.firstName(),
        command.lastName(),
        command.email(),
        command.phone(),
        command.address1(),
        command.address2(),
        command.city(),
        command.state(),
        command.postalCode(),
        command.country(),
        command.languagePreference(),
        command.favoriteCategoryId(),
        command.colorSchemePreference(),
        command.expectedVersion() + 1);
  }

  /**
   * 現在の認証プリンシパル自身のパスワードを変更する（#15 AC1〜AC2）。
   *
   * <p>userId起点で現在パスワードハッシュを取得し（{@link AccountRepository#findPasswordHashByUserId}）、{@link
   * PasswordEncoder#matches}で現在PWを再認証する（AC1）。不一致は{@link InvalidCurrentPasswordException}
   * （呼び出し元の{@code GlobalExceptionHandler}が422へ正規化）。成功時は新PWをbcryptエンコードして{@code
   * m_signon.password_hash}を更新し（AC2）、{@link AuthApplicationService#issueTokensFor}で現在セッションの
   * トークンをローテートする（Q3・#13登録の自動ログインと同じメソッドを再利用）。
   *
   * @throws org.springframework.security.access.AccessDeniedException 未認証
   * @throws ResourceNotFoundException 認証プリンシパルに対応する {@code m_signon} 行が存在しない場合
   * @throws InvalidCurrentPasswordException {@code command.currentPassword()} が格納ハッシュと一致しない場合
   */
  @Transactional
  public void changePassword(PasswordChangeCommand command, HttpServletResponse response) {
    AuthenticatedUser user = currentUserProvider.requireCurrentUser();
    String storedHash =
        accountRepository
            .findPasswordHashByUserId(user.userId())
            .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

    if (!passwordEncoder.matches(command.currentPassword(), storedHash)) {
      throw new InvalidCurrentPasswordException();
    }

    String newHash = passwordEncoder.encode(command.newPassword());
    accountRepository.updatePassword(user.userId(), newHash);

    authApplicationService.issueTokensFor(user, response);
  }
}
