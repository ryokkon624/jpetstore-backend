package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * {@code t_audit_write_quota} への読み書き Mapper（#39 AC3・N14・{@code AuditWriteQuotaService} から利用）。
 *
 * <p>{@code RegisterAttemptCustomMapper} と同方式（アノテーション方式・{@code backend-conventions} §9）だが、
 * ロックアウト（累積失敗で長期ロック）ではなく固定窓レート制限（一定時間内の書き込み回数を上限で頭打ちにし、 窓が切れたらリセットする）のため2文構成にしている: (1) {@link
 * #ensureRow} で行の存在を保証する no-op ON DUPLICATE KEY UPDATE（{@code INSERT IGNORE} は他エラーも警告化するため不採用）→ (2)
 * {@link #tryAcquire} の条件付き UPDATE で枠確保可否を affected rows として返す。
 *
 * <p>{@link #tryAcquire} の SET 句は MySQL の左→右評価に依存する。{@code window_expires_at} を最後に代入することで、 {@code
 * write_count}/{@code window_started_at} の IF 条件式は常に更新前（オリジナル）の {@code window_expires_at}
 * を参照する（窓が切れたか否かの判定が同一文内での他列の更新に影響されない）。
 */
@Mapper
public interface AuditWriteQuotaCustomMapper {

  @Insert(
      """
      INSERT INTO t_audit_write_quota
        (client_ip, write_count, suppressed_count, window_started_at, window_expires_at,
         create_program, update_program)
      VALUES
        (#{clientIp}, 0, 0, NULL, NULL, #{program}, #{program})
      ON DUPLICATE KEY UPDATE
        write_count = write_count
      """)
  int ensureRow(@Param("clientIp") String clientIp, @Param("program") String program);

  /**
   * 窓が切れていれば新規窓として write_count=1 にリセットし、切れていなければ write_count が {@code maxWrites}
   * 未満の場合のみインクリメントする。affected rows が1なら枠確保成功、0なら窓内上限に達しており抑止対象（呼び出し元が {@link #recordSuppressed}
   * を呼ぶ）。
   */
  @Update(
      """
      UPDATE t_audit_write_quota
         SET write_count = IF(window_expires_at IS NULL OR window_expires_at <= NOW(6),
                               1,
                               write_count + 1),
             window_started_at = IF(window_expires_at IS NULL OR window_expires_at <= NOW(6),
                                     NOW(6),
                                     window_started_at),
             update_program = #{program},
             window_expires_at = IF(window_expires_at IS NULL OR window_expires_at <= NOW(6),
                                     DATE_ADD(NOW(6), INTERVAL #{windowSeconds} SECOND),
                                     window_expires_at)
       WHERE client_ip = #{clientIp}
         AND (window_expires_at IS NULL OR window_expires_at <= NOW(6) OR write_count < #{maxWrites})
      """)
  int tryAcquire(
      @Param("clientIp") String clientIp,
      @Param("maxWrites") int maxWrites,
      @Param("windowSeconds") long windowSeconds,
      @Param("program") String program);

  /** 窓内上限超過により監査writeを抑止した事実を記録する（AC3「抑止が発生した事実自体は記録する」）。 */
  @Update(
      """
      UPDATE t_audit_write_quota
         SET suppressed_count = suppressed_count + 1,
             update_program = #{program}
       WHERE client_ip = #{clientIp}
      """)
  int recordSuppressed(@Param("clientIp") String clientIp, @Param("program") String program);
}
