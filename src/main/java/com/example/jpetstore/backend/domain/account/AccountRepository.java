package com.example.jpetstore.backend.domain.account;

import java.util.Optional;

/**
 * Account（氏名/連絡先/住所）の永続化アクセスの唯一の入口（#30・{@code backend-conventions} §1/§2/§9）。
 *
 * <p>実装は Infrastructure 層（{@code
 * infrastructure.mybatis.account.MyBatisAccountRepository}）に置く（依存性逆転）。 Application 層（{@code
 * AccountApplicationService}/{@code RegistrationApplicationService}）はこのインターフェイスのみに依存し、MyBatis の
 * {@code AccountContactCustomMapper} や {@code AccountContactCustomEntity} を直接扱わない。
 *
 * <p>{@link #findContactByUserId} は読み取り専用の CQRS 射影として扱う（{@code backend-conventions} §9）。{@link
 * AccountContact} は read-model の record をそのまま返し、集約の {@code reconstruct()} 再構築は強制しない。 Entity→record
 * 変換は Repository 実装内に閉じる。
 *
 * <p>#13: {@link #register} はユーザー登録（新規m_account/m_signon/m_profile INSERT）の唯一の入口。
 *
 * <p>#14: {@link #findEditDetailByUserId}/{@link #updateAccount} は本人のアカウント/プロフィール編集
 * （version楽観ロック）の唯一の入口。
 */
public interface AccountRepository {

  /**
   * 指定 {@code userId} の氏名/連絡先/住所を取得する（{@code m_account} 起点・1行）。
   *
   * @return 該当行が無ければ {@code Optional.empty()}（呼び出し元で404）
   */
  Optional<AccountContact> findContactByUserId(Long userId);

  /**
   * ユーザー登録: m_account/m_signon/m_profileへ順にINSERTする（#13 AC1・AC3）。
   *
   * @return 生成されたユーザーID（{@code m_account.user_id}・AUTO_INCREMENT）
   * @throws org.springframework.dao.DuplicateKeyException {@code username} が既存行と重複する場合（{@code
   *     uk_m_account_username} 違反・呼び出し元が409へ正規化する）
   */
  Long register(NewAccountRegistration registration);

  /**
   * 指定 {@code userId} の編集可能な全フィールド（氏名/連絡先/住所/langpref/favcategory）とversionを取得する （{@code
   * m_account}⋈{@code m_profile}・#14 AC3・E3）。
   *
   * @return 該当行が無ければ {@code Optional.empty()}（呼び出し元で404）
   */
  Optional<AccountEditDetail> findEditDetailByUserId(Long userId);

  /**
   * アカウント/プロフィールを更新する（#14 AC1〜AC3）。{@code m_account} は {@code version}楽観ロック付きUPDATE（{@code WHERE
   * user_id=:id AND version=:expectedVersion}）、 {@code m_profile}は同一トランザクション内で無ガードUPDATE（{@code
   * m_account.version}が編集アグリゲート 単一の楽観ロックトークン・E2）。{@code m_account}のUPDATEが競合（affected rows==0）した場合、
   * {@code m_profile}のUPDATEは行わない。
   *
   * @return {@code m_account}側UPDATEの影響行数（0=楽観ロック競合・呼び出し元が{@code
   *     AffectedRows.requireUpdated}で409へ正規化する。1=成功）
   */
  int updateAccount(AccountUpdate update);

  /**
   * 指定 {@code userId} の現在パスワードハッシュを取得する（#15 AC1・現在PW再認証用）。
   *
   * @return 該当行が無ければ {@code Optional.empty()}
   */
  Optional<String> findPasswordHashByUserId(Long userId);

  /**
   * 指定 {@code userId} のパスワードハッシュを更新する（#15 AC2）。{@code m_signon} はversion楽観ロック対象外 （WHEREガード無し・{@code
   * version + 1}は整合のため加算のみ・計画フェーズ確定）。
   *
   * @return 影響行数（通常1。0は対象userId不存在＝認証済みリクエストでは通常起こり得ない）
   */
  int updatePassword(Long userId, String newPasswordHash);
}
