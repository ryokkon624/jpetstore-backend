package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper;

import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AccountContactCustomEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * プリフィル用の氏名/連絡先/住所参照専用 Mapper（#7・{@code AccountApplicationService} から利用）。
 *
 * <p>{@code m_account} を {@code user_id} で1件取得する単純な SELECT のみのため、動的条件の無いアノテーション方式（{@code
 * backend-conventions} §9・{@code AccountAuthCustomMapper} と同方式）を採用する。SELECT のみに厳格限定し、
 * update/delete系メソッドは一切定義しない（E4/F4.2 編集側・#8 送信/在庫を先取りしない・計画フェーズ確定①）。
 */
@Mapper
public interface AccountContactCustomMapper {

  @Select(
      """
      SELECT first_name AS firstName, last_name AS lastName, email AS email, phone AS phone,
             address1 AS address1, address2 AS address2, city AS city, state AS state,
             postal_code AS postalCode, country AS country
        FROM m_account
       WHERE user_id = #{userId}
      """)
  AccountContactCustomEntity findByUserId(@Param("userId") Long userId);
}
