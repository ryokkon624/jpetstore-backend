package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper;

import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AuditLogCustomEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

/**
 * {@code t_audit_log} への書き込み専用 Mapper（AC7）。
 *
 * <p>MyBatis Generator 生成対象外（手書き）。理由は {@link AuditLogCustomEntity} の Javadoc 参照 （Sprint Review
 * 指摘⑥への回答: 追記専用テーブルのため update/delete を生成させたくない）。
 *
 * <p>アノテーション方式（{@code @Insert}）を採用。本 Mapper は INSERT 1 本のみの単機能で、 XML 化するほどの複雑な SQL・動的条件が無いため（hw-hub
 * の custom mapper は XML 主体だが、 本プロジェクトではメソッド単位でアノテーション/XML を使い分けてよいものとし、単純な CRUD は アノテーションで簡潔に書く）。
 */
@Mapper
public interface AuditLogCustomMapper {

  @Insert(
      """
      INSERT INTO t_audit_log
        (event_type, actor_user_id, actor_username, action, target_type, target_id, result, detail, client_ip,
         create_user_id, create_program, update_user_id, update_program)
      VALUES
        (#{eventType}, #{actorUserId}, #{actorUsername}, #{action}, #{targetType}, #{targetId}, #{result},
         #{detail}, #{clientIp}, #{createUserId}, #{createProgram}, #{updateUserId}, #{updateProgram})
      """)
  @Options(useGeneratedKeys = true, keyProperty = "auditId")
  int insert(AuditLogCustomEntity entity);
}
