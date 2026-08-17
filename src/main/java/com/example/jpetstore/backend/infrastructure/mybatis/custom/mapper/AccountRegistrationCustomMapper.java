package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper;

import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AccountRegistrationCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ProfileRegistrationCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.SignonRegistrationCustomEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * ユーザー登録（{@code m_account}/{@code m_signon}/{@code m_profile}）の永続化 Mapper（#13・{@code
 * MyBatisAccountRepository} から利用）。
 *
 * <p>{@link #insertAccount} はJOINを伴わないが生成キー（{@code useGeneratedKeys}）を用いるため、{@code
 * OrderCustomMapper} と同じくXMLマッパー方式を採用する（{@code backend-conventions} §9）。定義は {@code
 * resources/mapper/custom/AccountRegistrationCustomMapper.xml}。
 */
@Mapper
public interface AccountRegistrationCustomMapper {

  /** アカウントを1件INSERTする。{@code account.userId} に生成された AUTO_INCREMENT キーが補完される。 */
  void insertAccount(AccountRegistrationCustomEntity account);

  /** 認証情報（パスワードハッシュ）を1件INSERTする。 */
  void insertSignon(SignonRegistrationCustomEntity signon);

  /** プロフィールを1件INSERTする。 */
  void insertProfile(ProfileRegistrationCustomEntity profile);
}
