-- ------------------------------------------------------------
-- V00_000_012__create_register_attempt.sql
--   モダン版 JPetStore: ユーザー登録レート制限カウンタ（新規設計・legacyに存在しない）
--
--   参照: spec/behavior/account.md §2/§3/§5, spec/security-baseline.md SBD-6
--
--   #13 AC4/AC-neg2(SBD-6): 登録エンドポイントの総当り/列挙対策としてレート制限を設ける。
--   t_login_attempt（V00_000_007）と同型・同方式（分離表＋backend側の単文アトミック更新）だが、
--   キーは username ではなく client_ip とする：登録時点ではusernameがまだ存在しない
--   （これから作られる）ため、既存の t_login_attempt をそのまま転用できない。
--
--   client_ip を PK とし、m_account への外部キー制約を付けない：
--     - IPはアカウントに紐づく永続的な属性ではなく一過性のセキュリティ運用状態のため
--       （t_login_attempt.username no-FK・t_audit_log.actor_user_id no-FK と同じ判断）。
--   version 列は付与しない：t_login_attempt と同じ理由（単文アトミックUPDATEで完結するため
--   楽観ロック不要。architecture-conventions §4.3の「純追記表」とは異なるが同様にversion対象外）。
--
--   client_ip は IPv4/IPv6 双方を格納できるよう VARCHAR(45) とする（IPv6の最大表記長）。
--   X-Forwarded-Forは信頼しない（backend側でrequest.getRemoteAddr()を使用。Sprint2教訓）。
-- ------------------------------------------------------------

CREATE TABLE t_register_attempt (
      client_ip VARCHAR(45) NOT NULL COMMENT '登録試行元IPアドレス(PK・FK制約なし・IPv4/IPv6双方格納可)'
    , attempt_count INT NOT NULL DEFAULT 0 COMMENT '直近窓内の試行回数'
    , first_attempt_at DATETIME(6) NULL COMMENT '直近の連続試行の起点日時'
    , lock_until DATETIME(6) NULL COMMENT 'レート制限解除予定日時(NULL=制限なし)'
    -- WHO カラム標準ブロック
    , create_user_id BIGINT UNSIGNED NULL COMMENT '作成者ユーザID'
    , create_program VARCHAR(100) NOT NULL COMMENT '作成機能(ClassName#method)'
    , created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '作成日時'
    , update_user_id BIGINT UNSIGNED NULL COMMENT '更新者ユーザID'
    , update_program VARCHAR(100) NOT NULL COMMENT '更新機能(ClassName#method)'
    , updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                           ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新日時'
    , PRIMARY KEY (client_ip)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_ja_0900_as_cs COMMENT = 'ユーザー登録レート制限カウンタ';
