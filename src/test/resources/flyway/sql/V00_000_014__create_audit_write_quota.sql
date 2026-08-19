-- ------------------------------------------------------------
-- V00_000_014__create_audit_write_quota.sql
--   モダン版 JPetStore: 未認証由来の監査(t_audit_log) writeの窓内上限カウンタ（新規設計・legacyに存在しない）
--
--   参照: spec/security-baseline.md SBD-14, spec/architecture-conventions.md D7
--
--   #39 AC3(N14): 未認証リクエスト由来のAUTHZ_FAILURE監査writeが無制限だと、監査表そのものへの
--   フラッド攻撃（表の無制限成長・INSERTコスト）が成立してしまう。同一client_ipからの未認証write
--   を一定窓内で上限付きにする（D7＝secure-by-default系の試行カウンタ・レート制限状態はDB-backed）。
--   t_register_attempt（V00_000_012）をテンプレート化：client_ipをPKとし、m_accountへの外部キー
--   制約を付けない（IPはアカウントに紐づく永続的な属性ではなく一過性のセキュリティ運用状態のため。
--   t_login_attempt.username no-FK・t_register_attempt.client_ip no-FKと同じ判断）。
--   version列は付与しない：単文アトミックUPDATEで完結するため楽観ロック不要（t_register_attemptと同じ理由）。
--
--   t_login_attempt/t_register_attemptとの違い: あちらは「失敗/試行の累積でロックする」方式だが、
--   本表は「一定時間窓（window_expires_at）内の書き込み回数を上限で頭打ちにし、窓が切れたら
--   カウンタをリセットする」固定窓レート制限。suppressed_countは抑止が発生した事実の証跡
--   （AC3「抑止が発生した事実自体は記録またはログに残す」）。
--
--   client_ipはIPv4/IPv6双方を格納できるようVARCHAR(45)とする（IPv6の最大表記長）。
--   X-Forwarded-Forは信頼しない（backend側でrequest.getRemoteAddr()を使用。Sprint2教訓）。
-- ------------------------------------------------------------

CREATE TABLE t_audit_write_quota (
      client_ip VARCHAR(45) NOT NULL COMMENT '未認証監査write元IPアドレス(PK・FK制約なし・IPv4/IPv6双方格納可)'
    , write_count INT NOT NULL DEFAULT 0 COMMENT '直近窓内の監査write回数'
    , suppressed_count INT NOT NULL DEFAULT 0 COMMENT '窓内上限超過により抑止した監査write回数(抑止の証跡)'
    , window_started_at DATETIME(6) NULL COMMENT '直近窓の起点日時(NULL=未使用)'
    , window_expires_at DATETIME(6) NULL COMMENT '直近窓の失効予定日時(NULL=未使用)'
    -- WHO カラム標準ブロック
    , create_user_id BIGINT UNSIGNED NULL COMMENT '作成者ユーザID'
    , create_program VARCHAR(100) NOT NULL COMMENT '作成機能(ClassName#method)'
    , created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '作成日時'
    , update_user_id BIGINT UNSIGNED NULL COMMENT '更新者ユーザID'
    , update_program VARCHAR(100) NOT NULL COMMENT '更新機能(ClassName#method)'
    , updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                           ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新日時'
    , PRIMARY KEY (client_ip)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_ja_0900_as_cs COMMENT = '未認証監査write抑止カウンタ';
