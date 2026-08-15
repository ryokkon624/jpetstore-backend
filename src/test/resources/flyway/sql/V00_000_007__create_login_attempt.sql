-- ------------------------------------------------------------
-- V00_000_007__create_login_attempt.sql
--   モダン版 JPetStore: ログイン試行カウンタ（レート制限/ロックアウト。新規設計・legacyに存在しない）
--
--   参照: spec/behavior/auth.md §2/§3/§5(S10), spec/security-baseline.md SBD-6
--
--   #20 AC1(SBD-6): 総当り対策としてレート制限/ロックアウトを設ける。
--   保持先: Draft 1（分離表 + backend側の前段ゲート）を採用（Draft 2 = m_signon にロック列を持たせる案より
--   列挙耐性が高いため。詳細は migration-agent-base memory/dev/short_term.md 参照）。
--
--   username を PK とし、あえて m_account への外部キー制約を付けない：
--     - 未知username（存在しないアカウント）へのspray試行も同一の仕組みで抑止対象にする必要があり、
--       実在/非実在usernameを対称に扱わないと、ロック挙動の有無自体がユーザ存在のオラクルになってしまう
--       （t_audit_log.actor_user_id と同じ no-FK 判断）。
--   version 列は付与しない：一過性のセキュリティ運用状態であり、backend側は単文アトミックUPDATE
--   （INSERT ... ON DUPLICATE KEY UPDATE）で更新するため楽観ロックは不要（architecture-conventions §4.3
--   の「純追記表」とは異なるが、同様に version 管理対象外とする一過性テーブル）。
-- ------------------------------------------------------------

CREATE TABLE t_login_attempt (
      username VARCHAR(80) NOT NULL COMMENT 'ログイン試行対象のusername(PK・FK制約なし・実在/非実在を対称に扱い列挙を防ぐ)'
    , failed_attempt_count INT NOT NULL DEFAULT 0 COMMENT '連続失敗回数'
    , first_failed_at DATETIME(6) NULL COMMENT '直近の連続失敗の起点日時'
    , lock_until DATETIME(6) NULL COMMENT 'ロック解除予定日時(NULL=ロックなし)'
    -- WHO カラム標準ブロック
    , create_user_id BIGINT UNSIGNED NULL COMMENT '作成者ユーザID'
    , create_program VARCHAR(100) NOT NULL COMMENT '作成機能(ClassName#method)'
    , created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '作成日時'
    , update_user_id BIGINT UNSIGNED NULL COMMENT '更新者ユーザID'
    , update_program VARCHAR(100) NOT NULL COMMENT '更新機能(ClassName#method)'
    , updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                           ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新日時'
    , PRIMARY KEY (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_ja_0900_as_cs COMMENT = 'ログイン試行カウンタ(レート制限/ロックアウト)';
