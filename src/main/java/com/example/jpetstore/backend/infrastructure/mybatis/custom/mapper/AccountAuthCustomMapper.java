package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper;

import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AccountAuthCustomEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * ログイン照合用の参照専用 Mapper（#18・{@code JdbcUserDetailsService} から利用）。
 *
 * <p>{@code m_account}×{@code m_signon} を username で結合し1件取得する単純な SELECT のみのため、動的条件の無い
 * アノテーション方式（{@code backend-conventions} §9）を採用する。
 *
 * <p>⚠ auth.md §4 の罠（as-is の認証クエリが bannerdata と INNER JOIN しており、該当行が無いとログイン失敗する）は 新スキーマで bannerdata
 * 自体が廃止済みのため再発しない。本クエリでも bannerdata を参照しないこと。
 */
@Mapper
public interface AccountAuthCustomMapper {

  @Select(
      """
      SELECT a.user_id AS userId, a.username AS username, s.password_hash AS passwordHash
        FROM m_account a
        JOIN m_signon s ON s.user_id = a.user_id
       WHERE a.username = #{username}
      """)
  AccountAuthCustomEntity findByUsername(@Param("username") String username);
}
