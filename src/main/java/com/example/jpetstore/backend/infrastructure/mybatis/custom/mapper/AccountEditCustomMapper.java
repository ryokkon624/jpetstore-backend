package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper;

import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AccountEditCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AccountUpdateCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ProfileUpdateCustomEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * アカウント/プロフィール編集（{@code m_account}⋈{@code m_profile}）の永続化 Mapper（#14・{@code
 * MyBatisAccountRepository} から利用）。
 *
 * <p>{@link #findByUserId} はJOINを伴うため、{@link #updateAccount} はversion楽観ロック（コードベース初の実
 * UPDATE）を伴うため、いずれもXMLマッパー方式を採用する（{@code backend-conventions} §9・{@code
 * OrderCustomMapper}と同じ配置規約）。定義は {@code resources/mapper/custom/AccountEditCustomMapper.xml}。
 */
@Mapper
public interface AccountEditCustomMapper {

  /** 編集プリフィル用に本人の全編集可フィールドとversionを1件取得する（存在しなければ{@code null}）。 */
  AccountEditCustomEntity findByUserId(@Param("userId") Long userId);

  /**
   * {@code m_account}をversion楽観ロック付きで更新する（{@code WHERE user_id=:userId AND
   * version=:expectedVersion}）。
   *
   * @return 影響行数（0=競合・呼び出し元が409へ正規化する。1=成功）
   */
  int updateAccount(AccountUpdateCustomEntity account);

  /** {@code m_profile}を無ガードで更新する（{@code m_account.version}が編集アグリゲート単一の楽観ロックトークンのため）。 */
  void updateProfile(ProfileUpdateCustomEntity profile);
}
