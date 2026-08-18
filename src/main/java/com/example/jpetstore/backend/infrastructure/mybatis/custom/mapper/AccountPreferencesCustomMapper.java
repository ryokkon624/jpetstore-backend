package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper;

import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AccountPreferencesCustomEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * ログイン/再水和用のテーマ配色/言語設定参照専用 Mapper（#36/#25・{@code AccountApplicationService#getPreferences} から利用）。
 *
 * <p>{@code m_profile} を {@code user_id} で1件取得する単純な SELECT のみのため、動的条件の無いアノテーション方式（{@code
 * backend-conventions} §9・{@code AccountContactCustomMapper} と同方式）を採用する。SELECT のみに厳格限定する。
 */
@Mapper
public interface AccountPreferencesCustomMapper {

  @Select(
      """
      SELECT color_scheme_preference AS colorSchemePreference, language_preference AS languagePreference
        FROM m_profile
       WHERE user_id = #{userId}
      """)
  AccountPreferencesCustomEntity findByUserId(@Param("userId") Long userId);
}
