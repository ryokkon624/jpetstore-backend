package com.example.jpetstore.backend.infrastructure.audit;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

/** {@code t_audit_log} への書き込み専用 Mapper（AC7）。手書き（MyBatis Generator 生成対象外）。 */
@Mapper
public interface AuditLogMapper {

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
  int insert(AuditLogEntity entity);
}
