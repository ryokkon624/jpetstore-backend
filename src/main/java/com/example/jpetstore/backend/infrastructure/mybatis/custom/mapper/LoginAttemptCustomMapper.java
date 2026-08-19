package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * {@code t_login_attempt} への読み書き Mapper（#20 AC1・#41 AC1/AC4・SBD-6・{@code LoginAttemptService}
 * から利用）。
 *
 * <p>#41: ロック判定（旧 {@code countActiveLock}）と失敗計数（旧 {@code recordFailure}）が別クエリに分離していた check-then-act
 * 構造を解消し、資格情報照合（bcrypt）の前にスロットを原子的に確保する単一 UPDATE（{@link #acquireSlot}）へ統合した。(1) {@link #ensureRow}
 * で行の存在を保証する no-op ON DUPLICATE KEY UPDATE（{@code INSERT IGNORE} は他エラーも警告化するため不採用。seed値はDDLの {@code
 * DEFAULT} と一致する {@code failed_attempt_count=0}）→ (2) {@link #acquireSlot} の条件付き UPDATE で枠確保可否を
 * affected rows として返す。
 *
 * <p>{@link #acquireSlot} の SET 句は旧 {@code recordFailure} の SET 句を一字も変えず移植したもの（MySQL の 単一テーブル
 * UPDATE は SET 句を左から右へ評価し、後続の式が同一文内で既に代入済みの列の新しい値を参照できるという ドキュメント化された挙動に {@code lock_until}
 * の算出が意図的に依存している。同じ条件式を2箇所で独立に書くと 評価順依存の二重計算になり閾値判定が1回分ずれる不具合が過去に IT で発覚しているため、この依存関係を崩さない）。 WHERE
 * 句に {@code (lock_until IS NULL OR lock_until <= NOW(6))} を追加し、ロック中は UPDATE 自体をスキップする （affected
 * rows==0）ことで、ロック判定と計数を単一文に統合し、並行バーストでも {@code authenticate()} 到達回数が 閾値近傍で頭打ちになるようにした（InnoDB
 * の行ロックにより同一 username への UPDATE が直列化されるため）。
 */
@Mapper
public interface LoginAttemptCustomMapper {

  @Insert(
      """
      INSERT INTO t_login_attempt
        (username, failed_attempt_count, first_failed_at, lock_until, create_program, update_program)
      VALUES
        (#{username}, 0, NOW(6), NULL, #{program}, #{program})
      ON DUPLICATE KEY UPDATE
        failed_attempt_count = failed_attempt_count
      """)
  int ensureRow(@Param("username") String username, @Param("program") String program);

  /**
   * 照合前にスロットを原子的に確保する。ロック中（WHERE不一致）なら affected rows==0 となり、呼び出し元は {@code authenticate()}
   * へ進めずロック中として短絡する。affected rows==1 なら枠確保成功（新規窓リセット／通常インクリメント
   * のいずれか。窓が既に切れていれば新規窓として1にリセットし、そうでなければインクリメントする＝旧 {@code recordFailure} と同一の意味論）。
   */
  @Update(
      """
      UPDATE t_login_attempt
         SET failed_attempt_count = IF(lock_until IS NOT NULL AND lock_until <= NOW(6),
                                        1,
                                        failed_attempt_count + 1),
             first_failed_at = IF(lock_until IS NOT NULL AND lock_until <= NOW(6),
                                   NOW(6),
                                   first_failed_at),
             lock_until = IF(failed_attempt_count >= #{maxAttempts},
                              DATE_ADD(NOW(6), INTERVAL #{lockDurationSeconds} SECOND),
                              NULL),
             update_program = #{program}
       WHERE username = #{username}
         AND (lock_until IS NULL OR lock_until <= NOW(6))
      """)
  int acquireSlot(
      @Param("username") String username,
      @Param("maxAttempts") int maxAttempts,
      @Param("lockDurationSeconds") long lockDurationSeconds,
      @Param("program") String program);

  /** ログイン成功時にカウンタ/ロックをリセットする（行ごと削除。次回失敗時は新規窓として再作成される）。 */
  @Delete("DELETE FROM t_login_attempt WHERE username = #{username}")
  int recordSuccess(@Param("username") String username);
}
