package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * {@code t_login_attempt} への読み書き Mapper（#20 AC1・SBD-6・{@code LoginAttemptService} から利用）。
 *
 * <p>アノテーション方式を採用（{@code backend-conventions} §9）。{@link #recordFailure} は INSERT ... ON DUPLICATE
 * KEY UPDATE による単文アトミック更新（並行リクエストでのカウンタ取りこぼしを防ぐ）。ロック期間が既に経過している行への失敗はカウンタを新規窓として 1
 * にリセットする（ロック解除後は毎回 即座に再ロックされないようにするため）。
 *
 * <p>{@link #countActiveLock} はロック判定を DB 側の {@code NOW(6)} で行う（アプリ側の時計と DB 側の時計がずれる環境 （例:
 * JVM=ローカルタイムゾーン・DBコンテナ=UTC）でも安全に判定するため。Java側で取得した {@code lock_until} を app の
 * 現在時刻と比較する実装は、クロックスキュー環境でロック判定が機能しない不具合が IT で実際に発覚したため採用しない）。
 *
 * <p>{@link #recordFailure} の {@code lock_until} 算出は、直前の {@code failed_attempt_count}
 * 代入の結果を再利用する（MySQL の 単一テーブル UPDATE／{@code ON DUPLICATE KEY UPDATE} は SET
 * 句を左から右へ評価し、後続の式は同一文内で既に代入済みの列の新しい値を参照できる、という
 * ドキュメント化された挙動を利用）。同じ条件式を2箇所で独立に書くと評価順依存の二重計算になり閾値判定が1回分ずれるため（IT で実際に発覚）、あえて依存させている。
 */
@Mapper
public interface LoginAttemptCustomMapper {

  @Select(
      """
      SELECT COUNT(*) FROM t_login_attempt
       WHERE username = #{username} AND lock_until IS NOT NULL AND lock_until > NOW(6)
      """)
  int countActiveLock(@Param("username") String username);

  /**
   * ログイン失敗を記録する。行が無ければ新規作成（count=1）。行があれば、既存の {@code lock_until} が既に経過していれば新規窓として count=1
   * にリセットし、そうでなければ count をインクリメントする。インクリメント後の count が {@code maxAttempts} 以上なら {@code lock_until} を
   * {@code lockDurationSeconds} 秒先に設定し、そうでなければ {@code lock_until} は NULL のままにする。
   */
  @Insert(
      """
      INSERT INTO t_login_attempt
        (username, failed_attempt_count, first_failed_at, lock_until, create_program, update_program)
      VALUES
        (#{username}, 1, NOW(6), NULL, #{program}, #{program})
      ON DUPLICATE KEY UPDATE
        failed_attempt_count = IF(lock_until IS NOT NULL AND lock_until <= NOW(6),
                                   1,
                                   failed_attempt_count + 1),
        first_failed_at = IF(lock_until IS NOT NULL AND lock_until <= NOW(6),
                              NOW(6),
                              first_failed_at),
        lock_until = IF(failed_attempt_count >= #{maxAttempts},
                         DATE_ADD(NOW(6), INTERVAL #{lockDurationSeconds} SECOND),
                         NULL),
        update_program = #{program}
      """)
  int recordFailure(
      @Param("username") String username,
      @Param("maxAttempts") int maxAttempts,
      @Param("lockDurationSeconds") long lockDurationSeconds,
      @Param("program") String program);

  /** ログイン成功時にカウンタ/ロックをリセットする（行ごと削除。次回失敗時は新規窓として再作成される）。 */
  @Delete("DELETE FROM t_login_attempt WHERE username = #{username}")
  int recordSuccess(@Param("username") String username);
}
