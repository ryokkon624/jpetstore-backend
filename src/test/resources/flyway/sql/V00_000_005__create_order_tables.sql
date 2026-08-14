-- ------------------------------------------------------------
-- V00_000_005__create_order_tables.sql
--   モダン版 JPetStore: 注文ドメイン（order / order_line / inventory）テーブル定義
--
--   移行元: legacy-jpetstore/db/mysql/jpetstore-mysql-schema.sql
--           orders / lineitem / orderstatus / inventory / sequence
--   参照: spec/behavior/order.md, spec/architecture-conventions.md §2/§4,
--         spec/intended-diff-ledger.md (ID-8, ID-21, ID-22, ID-23)
--
--   AC4(SBD-13): 金額列（total_price/unit_price）は decimal（doubleを使わない）。
--   AC5(F3.6): カード関連列（creditcard/exprdate/cardtype）は持たない（ID-8）。
--   ID-21: courier/locale は持たない（プレースホルダ機能のためスコープ簡素化）。
--   ID-22: orderstatus テーブルは廃止。t_order.status_code(placeholder) を持ち、
--          状態変更は t_audit_log（V00_000_006）に記録する。
--   ID-23: sequence テーブルは廃止。orderId は AUTO_INCREMENT で原子採番する。
--   代理キー: t_order.order_id BIGINT AUTO_INCREMENT。t_order.user_id は m_account.user_id を参照。
--   並行制御: t_order/t_order_line は純追記表、t_inventory はガード付きアトミック減算
--            （architecture-conventions §4.1）が主機構のため、いずれも version 列は付与しない
--            （§4.3）。
-- ------------------------------------------------------------

/* ============================================================
   t_order : 注文ヘッダ（← legacy orders）
   ============================================================ */
CREATE TABLE t_order (
      order_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '注文ID(代理キー・旧sequence採番を置換)'
    , user_id BIGINT UNSIGNED NOT NULL COMMENT 'ユーザID'
    , order_date DATE NOT NULL COMMENT '注文日'
    , ship_address1 VARCHAR(80) NOT NULL COMMENT '配送先住所1'
    , ship_address2 VARCHAR(80) NULL COMMENT '配送先住所2'
    , ship_city VARCHAR(80) NOT NULL COMMENT '配送先市区町村'
    , ship_state VARCHAR(80) NOT NULL COMMENT '配送先都道府県/州'
    , ship_postal_code VARCHAR(20) NOT NULL COMMENT '配送先郵便番号'
    , ship_country VARCHAR(20) NOT NULL COMMENT '配送先国'
    , bill_address1 VARCHAR(80) NOT NULL COMMENT '請求先住所1'
    , bill_address2 VARCHAR(80) NULL COMMENT '請求先住所2'
    , bill_city VARCHAR(80) NOT NULL COMMENT '請求先市区町村'
    , bill_state VARCHAR(80) NOT NULL COMMENT '請求先都道府県/州'
    , bill_postal_code VARCHAR(20) NOT NULL COMMENT '請求先郵便番号'
    , bill_country VARCHAR(20) NOT NULL COMMENT '請求先国'
    , total_price DECIMAL(10,2) NOT NULL COMMENT '合計金額(サーバ再計算・AC4)'
    , bill_to_first_name VARCHAR(80) NOT NULL COMMENT '請求先名'
    , bill_to_last_name VARCHAR(80) NOT NULL COMMENT '請求先姓'
    , ship_to_first_name VARCHAR(80) NOT NULL COMMENT '配送先名'
    , ship_to_last_name VARCHAR(80) NOT NULL COMMENT '配送先姓'
    , status_code VARCHAR(10) NOT NULL COMMENT '注文ステータス(placeholder・状態変更はaudit_logに記録)'
    -- WHO カラム標準ブロック
    , create_user_id BIGINT UNSIGNED NULL COMMENT '作成者ユーザID'
    , create_program VARCHAR(100) NOT NULL COMMENT '作成機能(ClassName#method)'
    , created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '作成日時'
    , update_user_id BIGINT UNSIGNED NULL COMMENT '更新者ユーザID'
    , update_program VARCHAR(100) NOT NULL COMMENT '更新機能(ClassName#method)'
    , updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                           ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新日時(監査専用・ロックには使わない)'
    , PRIMARY KEY (order_id)
    , KEY idx_t_order_user_id (user_id)
    , CONSTRAINT fk_t_order_user_id FOREIGN KEY (user_id)
        REFERENCES m_account (user_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_ja_0900_as_cs COMMENT = '注文ヘッダ';

/* ============================================================
   t_order_line : 注文明細（← legacy lineitem）
   ============================================================ */
CREATE TABLE t_order_line (
      order_line_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '注文明細ID(代理キー)'
    , order_id BIGINT UNSIGNED NOT NULL COMMENT '注文ID'
    , line_num INT NOT NULL COMMENT '明細行番号'
    , item_id VARCHAR(10) NOT NULL COMMENT 'アイテムID'
    , quantity INT NOT NULL COMMENT '数量'
    , unit_price DECIMAL(10,2) NOT NULL COMMENT '単価(注文確定時点のマスター価格・AC4)'
    , create_user_id BIGINT UNSIGNED NULL COMMENT '作成者ユーザID'
    , create_program VARCHAR(100) NOT NULL COMMENT '作成機能(ClassName#method)'
    , created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '作成日時'
    , update_user_id BIGINT UNSIGNED NULL COMMENT '更新者ユーザID'
    , update_program VARCHAR(100) NOT NULL COMMENT '更新機能(ClassName#method)'
    , updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                           ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新日時'
    , PRIMARY KEY (order_line_id)
    , UNIQUE KEY uk_t_order_line_order_id_line_num (order_id, line_num)
    , KEY idx_t_order_line_item_id (item_id)
    , CONSTRAINT fk_t_order_line_order_id FOREIGN KEY (order_id)
        REFERENCES t_order (order_id) ON DELETE RESTRICT ON UPDATE RESTRICT
    , CONSTRAINT fk_t_order_line_item_id FOREIGN KEY (item_id)
        REFERENCES m_item (item_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_ja_0900_as_cs COMMENT = '注文明細';

/* ============================================================
   t_inventory : 在庫（← legacy inventory）
     - 並行制御はガード付きアトミック減算（architecture-conventions §4.1）が主機構。
       version列・SELECT ... FOR UPDATEは不要。
   ============================================================ */
CREATE TABLE t_inventory (
      item_id VARCHAR(10) NOT NULL COMMENT 'アイテムID'
    , quantity INT NOT NULL COMMENT '在庫数(ガード付きアトミック減算で更新)'
    , create_user_id BIGINT UNSIGNED NULL COMMENT '作成者ユーザID'
    , create_program VARCHAR(100) NOT NULL COMMENT '作成機能(ClassName#method)'
    , created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '作成日時'
    , update_user_id BIGINT UNSIGNED NULL COMMENT '更新者ユーザID'
    , update_program VARCHAR(100) NOT NULL COMMENT '更新機能(ClassName#method)'
    , updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                           ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新日時'
    , PRIMARY KEY (item_id)
    , CONSTRAINT fk_t_inventory_item_id FOREIGN KEY (item_id)
        REFERENCES m_item (item_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_ja_0900_as_cs COMMENT = '在庫';
