package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * {@code t_register_attempt} への読み書き Mapper（#13 AC4/AC-neg2・#41 AC2/AC4・SBD-6・{@code
 * RegisterAttemptService} から利用）。
 *
 * <p>#41: {@link LoginAttemptCustomMapper} と同じ2文イディオムへ置き換えた。判定（旧 {@code countActiveLock}）と 計数（旧
 * {@code recordAttempt}）が別クエリに分離していた check-then-act 構造を解消し、登録処理の前にスロットを 原子的に確保する単一 UPDATE（{@link
 * #acquireSlot}）へ統合した。(1) {@link #ensureRow} で行の存在を保証する no-op ON DUPLICATE KEY UPDATE（seed値はDDLの
 * {@code DEFAULT} と一致する {@code attempt_count=0}）→ (2) {@link #acquireSlot} の条件付き UPDATE で枠確保可否を
 * affected rows として返す。
 *
 * <p>{@link #acquireSlot} の SET 句は旧 {@code recordAttempt} の SET 句を一字も変えず移植したもの（{@link
 * LoginAttemptCustomMapper#acquireSlot} と同じく MySQL の左→右評価に依存する {@code lock_until} 算出）。ロック判定は WHERE
 * 句内の {@code lock_until <= NOW(6)} 比較により DB 側の時刻基準で行う（クロックスキュー環境でも安全に判定するため。 {@code
 * LoginAttemptCustomMapper}と同じ設計判断）。
 */
@Mapper
public interface RegisterAttemptCustomMapper {

  @Insert(
      """
      INSERT INTO t_register_attempt
        (client_ip, attempt_count, first_attempt_at, lock_until, create_program, update_program)
      VALUES
        (#{clientIp}, 0, NOW(6), NULL, #{program}, #{program})
      ON DUPLICATE KEY UPDATE
        attempt_count = attempt_count
      """)
  int ensureRow(@Param("clientIp") String clientIp, @Param("program") String program);

  /**
   * 登録処理の前にスロットを原子的に確保する。ロック中（WHERE不一致）なら affected rows==0 となり、呼び出し元は
   * 登録処理へ進めずレート制限中として短絡する。affected rows==1 なら枠確保成功（新規窓リセット／通常インクリメント のいずれか。旧 {@code recordAttempt}
   * と同一の意味論）。
   */
  @Update(
      """
      UPDATE t_register_attempt
         SET attempt_count = IF(lock_until IS NOT NULL AND lock_until <= NOW(6),
                                 1,
                                 attempt_count + 1),
             first_attempt_at = IF(lock_until IS NOT NULL AND lock_until <= NOW(6),
                                    NOW(6),
                                    first_attempt_at),
             lock_until = IF(attempt_count >= #{maxAttempts},
                              DATE_ADD(NOW(6), INTERVAL #{lockDurationSeconds} SECOND),
                              NULL),
             update_program = #{program}
       WHERE client_ip = #{clientIp}
         AND (lock_until IS NULL OR lock_until <= NOW(6))
      """)
  int acquireSlot(
      @Param("clientIp") String clientIp,
      @Param("maxAttempts") int maxAttempts,
      @Param("lockDurationSeconds") long lockDurationSeconds,
      @Param("program") String program);
}
