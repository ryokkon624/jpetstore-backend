package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * {@code t_register_attempt} への読み書き Mapper（#13 AC4/AC-neg2・SBD-6・{@code RegisterAttemptService}
 * から利用）。
 *
 * <p>{@code LoginAttemptCustomMapper}と同方式（アノテーション方式・{@code backend-conventions} §9）。{@link
 * #recordAttempt} は INSERT ... ON DUPLICATE KEY UPDATE による単文アトミック更新（並行リクエストでのカウンタ取りこぼしを防ぐ）。
 * ロック期間が既に経過している行への試行はカウンタを新規窓として1にリセットする。
 *
 * <p>{@link #countActiveLock} はロック判定を DB 側の {@code NOW(6)} で行う（クロックスキュー環境でも安全に判定するため。 {@code
 * LoginAttemptCustomMapper}と同じ設計判断）。
 */
@Mapper
public interface RegisterAttemptCustomMapper {

  @Select(
      """
      SELECT COUNT(*) FROM t_register_attempt
       WHERE client_ip = #{clientIp} AND lock_until IS NOT NULL AND lock_until > NOW(6)
      """)
  int countActiveLock(@Param("clientIp") String clientIp);

  /**
   * 登録試行を記録する。行が無ければ新規作成（count=1）。行があれば、既存の {@code lock_until} が既に経過していれば新規窓として count=1
   * にリセットし、そうでなければ count をインクリメントする。インクリメント後の count が {@code maxAttempts} 以上なら {@code lock_until} を
   * {@code lockDurationSeconds} 秒先に設定し、そうでなければ {@code lock_until} は NULL のままにする。
   */
  @Insert(
      """
      INSERT INTO t_register_attempt
        (client_ip, attempt_count, first_attempt_at, lock_until, create_program, update_program)
      VALUES
        (#{clientIp}, 1, NOW(6), NULL, #{program}, #{program})
      ON DUPLICATE KEY UPDATE
        attempt_count = IF(lock_until IS NOT NULL AND lock_until <= NOW(6),
                            1,
                            attempt_count + 1),
        first_attempt_at = IF(lock_until IS NOT NULL AND lock_until <= NOW(6),
                               NOW(6),
                               first_attempt_at),
        lock_until = IF(attempt_count >= #{maxAttempts},
                         DATE_ADD(NOW(6), INTERVAL #{lockDurationSeconds} SECOND),
                         NULL),
        update_program = #{program}
      """)
  int recordAttempt(
      @Param("clientIp") String clientIp,
      @Param("maxAttempts") int maxAttempts,
      @Param("lockDurationSeconds") long lockDurationSeconds,
      @Param("program") String program);
}
