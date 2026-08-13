-- ------------------------------------------------------------
-- V00_000_003__create_catalog_tables.sql
--   モダン版 JPetStore: カタログドメイン（商品階層）テーブル定義
--
--   移行元: legacy-jpetstore/db/mysql/jpetstore-mysql-schema.sql
--           supplier / category / product / item
--   参照: spec/behavior/catalog.md, spec/architecture-conventions.md §2/§4
--
--   種別: いずれも参照マスタ（読取専用・アプリからの更新なし）＝ version 列は付与しない
--         （architecture-conventions §4.3）。WHO6列は全業務表共通ボイラープレートとして付与。
--   代理キー: カタログは自然キーを維持（legacy の suppid/catid/productid/itemid をそのまま
--             m_xxx.xxx_id として引き継ぐ。FI-SW-01 等の公開 business code のため）。
-- ------------------------------------------------------------

/* ============================================================
   m_supplier : サプライヤマスタ（← legacy supplier）
   ============================================================ */
CREATE TABLE m_supplier (
      supplier_id INT NOT NULL COMMENT 'サプライヤID(自然キー)'
    , name VARCHAR(80) NULL COMMENT 'サプライヤ名'
    , status VARCHAR(2) NOT NULL COMMENT 'ステータス'
    , address1 VARCHAR(80) NULL COMMENT '住所1'
    , address2 VARCHAR(80) NULL COMMENT '住所2'
    , city VARCHAR(80) NULL COMMENT '市区町村'
    , state VARCHAR(80) NULL COMMENT '都道府県/州'
    , postal_code VARCHAR(10) NULL COMMENT '郵便番号'
    , phone VARCHAR(80) NULL COMMENT '電話番号'
    -- WHO カラム標準ブロック（正典: architecture-conventions.md §2）
    , create_user_id BIGINT UNSIGNED NULL COMMENT '作成者ユーザID'
    , create_program VARCHAR(100) NOT NULL COMMENT '作成機能(ClassName#method)'
    , created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '作成日時'
    , update_user_id BIGINT UNSIGNED NULL COMMENT '更新者ユーザID'
    , update_program VARCHAR(100) NOT NULL COMMENT '更新機能(ClassName#method)'
    , updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                           ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新日時'
    , PRIMARY KEY (supplier_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_ja_0900_as_cs COMMENT = 'サプライヤマスタ';

/* ============================================================
   m_category : カテゴリマスタ（← legacy category）
   ============================================================ */
CREATE TABLE m_category (
      category_id VARCHAR(10) NOT NULL COMMENT 'カテゴリID(自然キー)'
    , name VARCHAR(80) NULL COMMENT 'カテゴリ名'
    , description VARCHAR(255) NULL COMMENT '説明'
    , create_user_id BIGINT UNSIGNED NULL COMMENT '作成者ユーザID'
    , create_program VARCHAR(100) NOT NULL COMMENT '作成機能(ClassName#method)'
    , created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '作成日時'
    , update_user_id BIGINT UNSIGNED NULL COMMENT '更新者ユーザID'
    , update_program VARCHAR(100) NOT NULL COMMENT '更新機能(ClassName#method)'
    , updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                           ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新日時'
    , PRIMARY KEY (category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_ja_0900_as_cs COMMENT = 'カテゴリマスタ';

/* ============================================================
   m_product : 商品マスタ（← legacy product）
   ============================================================ */
CREATE TABLE m_product (
      product_id VARCHAR(10) NOT NULL COMMENT '商品ID(自然キー)'
    , category_id VARCHAR(10) NOT NULL COMMENT 'カテゴリID'
    , name VARCHAR(80) NULL COMMENT '商品名'
    , description VARCHAR(255) NULL COMMENT '説明'
    , create_user_id BIGINT UNSIGNED NULL COMMENT '作成者ユーザID'
    , create_program VARCHAR(100) NOT NULL COMMENT '作成機能(ClassName#method)'
    , created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '作成日時'
    , update_user_id BIGINT UNSIGNED NULL COMMENT '更新者ユーザID'
    , update_program VARCHAR(100) NOT NULL COMMENT '更新機能(ClassName#method)'
    , updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                           ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新日時'
    , PRIMARY KEY (product_id)
    , KEY idx_m_product_category_id (category_id)
    , KEY idx_m_product_name (name)
    , CONSTRAINT fk_m_product_category_id FOREIGN KEY (category_id)
        REFERENCES m_category (category_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_ja_0900_as_cs COMMENT = '商品マスタ';

/* ============================================================
   m_item : 在庫アイテムマスタ（← legacy item）
     - list_price / unit_cost は AC4(SBD-13) により decimal(10,2)
   ============================================================ */
CREATE TABLE m_item (
      item_id VARCHAR(10) NOT NULL COMMENT 'アイテムID(自然キー)'
    , product_id VARCHAR(10) NOT NULL COMMENT '商品ID'
    , list_price DECIMAL(10,2) NULL COMMENT '販売価格'
    , unit_cost DECIMAL(10,2) NULL COMMENT '原価'
    , supplier_id INT NULL COMMENT 'サプライヤID'
    , status VARCHAR(2) NULL COMMENT 'ステータス'
    , attribute1 VARCHAR(80) NULL COMMENT '属性1'
    , attribute2 VARCHAR(80) NULL COMMENT '属性2'
    , attribute3 VARCHAR(80) NULL COMMENT '属性3'
    , attribute4 VARCHAR(80) NULL COMMENT '属性4'
    , attribute5 VARCHAR(80) NULL COMMENT '属性5'
    , create_user_id BIGINT UNSIGNED NULL COMMENT '作成者ユーザID'
    , create_program VARCHAR(100) NOT NULL COMMENT '作成機能(ClassName#method)'
    , created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '作成日時'
    , update_user_id BIGINT UNSIGNED NULL COMMENT '更新者ユーザID'
    , update_program VARCHAR(100) NOT NULL COMMENT '更新機能(ClassName#method)'
    , updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                           ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新日時'
    , PRIMARY KEY (item_id)
    , KEY idx_m_item_product_id (product_id)
    , KEY idx_m_item_supplier_id (supplier_id)
    , CONSTRAINT fk_m_item_product_id FOREIGN KEY (product_id)
        REFERENCES m_product (product_id) ON DELETE RESTRICT ON UPDATE RESTRICT
    , CONSTRAINT fk_m_item_supplier_id FOREIGN KEY (supplier_id)
        REFERENCES m_supplier (supplier_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_ja_0900_as_cs COMMENT = '在庫アイテムマスタ';
