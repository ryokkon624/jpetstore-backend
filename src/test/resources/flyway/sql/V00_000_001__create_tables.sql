-- ------------------------------------------------------------
-- V00_000_001__create_tables.sql
--   モダン版 JPetStore: テーブル定義
--
--   このマイグレーションでは m_code（区分値マスタ）のみを作成する。
--   ドメイン業務テーブル（account / product / order 等）は Phase 3 で
--   PO の仕様から起こすため、ここでは作らない。
-- ------------------------------------------------------------
--
-- ============================================================
--  WHO カラム標準ブロック（全業務テーブル共通ボイラープレート）
--  正典: migration-agent-base/spec/architecture-conventions.md §2
--        migration-agent-base/.claude/rules/database.md
--
--    , create_user_id BIGINT UNSIGNED NULL      COMMENT '作成者ユーザID'
--    , create_program VARCHAR(100)    NOT NULL  COMMENT '作成機能(ClassName#method)'
--    , created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '作成日時'
--    , update_user_id BIGINT UNSIGNED NULL      COMMENT '更新者ユーザID'
--    , update_program VARCHAR(100)    NOT NULL  COMMENT '更新機能(ClassName#method)'
--    , updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
--                                      ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新日時'
--
--  - create_program / update_program は ProgramType enum を廃止し、
--    ClassName#method のテキスト（例 OrderService#placeOrder）を格納する。
--    m_code の code_type 0012（ProgramType）は作らない。
--  - 値は AOP + MyBatis Interceptor が自動付与（最外の業務サービスが勝つ set-once）。
--    マスターデータの seed（Flyway の INSERT）では Interceptor が効かないため、
--    リテラル 'INIT_DATA' を明示する。
-- ============================================================

/* ============================================================
   m_code : コードマスタ（区分値）
     - 多言語は日英のみ（display_name_ja / display_name_en）。es 列は持たない。
     - WHO カラム 6 列を末尾に付与。
     - PRIMARY KEY (code_type, code_value)
   ============================================================ */
CREATE TABLE m_code(
                       code_type VARCHAR (4) NOT NULL COMMENT 'コード種別(4桁数字文字列)'
    , code_type_name VARCHAR (100) NOT NULL COMMENT 'コード種別名(日本語)'
    , code_type_name_en VARCHAR (100) NOT NULL COMMENT 'コード種別名(英語)。enum のクラス名になる'
    , code_value VARCHAR (10) NOT NULL COMMENT 'コード値'
    , name VARCHAR (100) NOT NULL COMMENT '値の識別名(英語)'
    , display_name_ja VARCHAR (100) NULL COMMENT '表示名(日本語)'
    , display_name_en VARCHAR (100) NULL COMMENT '表示名(英語)'
    , remarks VARCHAR (255) NULL COMMENT '備考'
    , display_order VARCHAR (5) NULL COMMENT '表示順(10001刻み推奨)'
    -- WHO カラム標準ブロック（先頭コメント参照）
    , create_user_id BIGINT UNSIGNED NULL COMMENT '作成者ユーザID'
    , create_program VARCHAR (100) NOT NULL COMMENT '作成機能(ClassName#method)'
    , created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP (6) COMMENT '作成日時'
    , update_user_id BIGINT UNSIGNED NULL COMMENT '更新者ユーザID'
    , update_program VARCHAR (100) NOT NULL COMMENT '更新機能(ClassName#method)'
    , updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP (6)
                           ON UPDATE CURRENT_TIMESTAMP (6) COMMENT '更新日時'
    , PRIMARY KEY (code_type, code_value)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_ja_0900_as_cs COMMENT = 'コードマスタ';
