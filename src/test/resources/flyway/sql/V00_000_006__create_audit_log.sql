-- ------------------------------------------------------------
-- V00_000_006__create_audit_log.sql
--   モダン版 JPetStore: 監査ログ基盤（新規設計・legacyに存在しない）
--
--   参照: spec/security-baseline.md SBD-14, spec/architecture-conventions.md §2
--
--   AC6(SBD-14): 認可失敗・状態変更（注文作成等）を「誰が/何を/結果」で記録する先を用意する。
--   Sprint1のスコープは記録先テーブルの用意まで（記録ロジックの実装はbackend側の後続Sprint）。
--   ID-22: orderstatus 廃止に伴う状態変更の記録先もこのテーブルを使う。
--
--   actor_user_id はあえて m_account への外部キー制約を付けない：
--     - ログイン失敗（存在しないusernameへの試行）はそもそも実在user_idを持たない
--     - アカウント削除後も監査証跡は残す必要があるため、FKでライフサイクルを縛らない
--     denormalizeした actor_username を併せて保持し、削除後も追跡可能にする。
--   version 列は付与しない（追記専用ログ・architecture-conventions §4.3）。
-- ------------------------------------------------------------

CREATE TABLE t_audit_log (
      audit_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '監査ログID'
    , event_type VARCHAR(20) NOT NULL COMMENT 'イベント種別(AUTHZ_FAILURE/STATE_CHANGE)'
    , actor_user_id BIGINT UNSIGNED NULL COMMENT '実行者ユーザID(未認証/不明時はNULL・FK制約なし)'
    , actor_username VARCHAR(80) NULL COMMENT '実行者ユーザ名(非正規化・アカウント削除後も追跡可能にする)'
    , action VARCHAR(100) NOT NULL COMMENT '操作(例: ORDER_CREATE, LOGIN, EDIT_ACCOUNT)'
    , target_type VARCHAR(50) NULL COMMENT '対象リソース種別(例: ORDER, ACCOUNT)'
    , target_id VARCHAR(50) NULL COMMENT '対象リソースID'
    , result VARCHAR(10) NOT NULL COMMENT '結果(SUCCESS/DENIED/FAILURE)'
    , detail JSON NULL COMMENT '詳細情報(構造化)'
    , client_ip VARCHAR(45) NULL COMMENT '接続元IP(IPv6対応・最大45文字)'
    -- WHO カラム標準ブロック（発生日時は created_at を流用）
    , create_user_id BIGINT UNSIGNED NULL COMMENT '作成者ユーザID'
    , create_program VARCHAR(100) NOT NULL COMMENT '作成機能(ClassName#method)'
    , created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '作成日時(=発生日時)'
    , update_user_id BIGINT UNSIGNED NULL COMMENT '更新者ユーザID'
    , update_program VARCHAR(100) NOT NULL COMMENT '更新機能(ClassName#method)'
    , updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                           ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新日時'
    , PRIMARY KEY (audit_id)
    , KEY idx_t_audit_log_event_type_created_at (event_type, created_at)
    , KEY idx_t_audit_log_actor_user_id (actor_user_id)
    , KEY idx_t_audit_log_target (target_type, target_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_ja_0900_as_cs COMMENT = '監査ログ';
