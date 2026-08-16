-- ------------------------------------------------------------
-- V00_000_010__create_cart_tables.sql
--   モダン版 JPetStore: カートドメイン（t_cart / t_cart_item）テーブル定義
--
--   移行元: legacy-jpetstore はカートをセッションオブジェクトのみで保持（DB永続なし・
--           domain/Cart.java, CartItem.java, CartActionForm.java）。
--   参照: spec/behavior/cart.md, spec/architecture-conventions.md §4.3,
--         backlog/sprint_08/sprint_backlog.md（計画フェーズ確定事項①②③）
--
--   計画フェーズ確定事項①: サーバーカートの永続スコープ = DB永続（backendはSTATELESS/JWTのため
--   in-memory/sessionは不可）。所有者は m_account.user_id を FK に張る（t_orderと同じ代理キー）。
--   未ログインはfrontend側でlocalStorage保持（本マイグレーションの対象外）。
--
--   D3承認（意図的逸脱・architecture-conventions §4.3の例外）: t_cart_item には version 列を
--   付与しない。単一所有者・暫定（購入確定前）カートであり、複数編集者による競合が起きない
--   last-write-win 前提のエンティティのため、§4.3の楽観ロック対象（多編集者エンティティ）には
--   該当しない。加算はON DUPLICATE KEY UPDATEによる単文アトミック更新で担保する。
--
--   単一表 t_cart_item + UNIQUE(cart_id, item_id) により、legacy Cart.java の itemMap/itemList
--   二重構造 desync（幽霊行バグ・intended-diff-ledger ID-17）を構造的に不能にする（AC2）。
--
--   並行制御: t_cart/t_cart_item は暫定状態のトランザクションテーブルのため、t_order/t_order_line
--   （純追記表）と同様 version 列は付与しない（t_cart_itemはD3・t_cartは1ユーザ1行でUPDATE自体を
--   行わないため不要）。
-- ------------------------------------------------------------

/* ============================================================
   t_cart : カートヘッダ（新規・legacyにDB表なし）
     - 1ユーザ1カート（user_id UNIQUE）。ensureCartでの初回作成のみ・以後UPDATEなし。
   ============================================================ */
CREATE TABLE t_cart (
      cart_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'カートID(代理キー)'
    , user_id BIGINT UNSIGNED NOT NULL COMMENT 'ユーザID(1ユーザ1カート)'
    -- WHO カラム標準ブロック
    , create_user_id BIGINT UNSIGNED NULL COMMENT '作成者ユーザID'
    , create_program VARCHAR(100) NOT NULL COMMENT '作成機能(ClassName#method)'
    , created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '作成日時'
    , update_user_id BIGINT UNSIGNED NULL COMMENT '更新者ユーザID'
    , update_program VARCHAR(100) NOT NULL COMMENT '更新機能(ClassName#method)'
    , updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                           ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新日時'
    , PRIMARY KEY (cart_id)
    , UNIQUE KEY uk_t_cart_user_id (user_id)
    , CONSTRAINT fk_t_cart_user_id FOREIGN KEY (user_id)
        REFERENCES m_account (user_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_ja_0900_as_cs COMMENT = 'カートヘッダ';

/* ============================================================
   t_cart_item : カート明細（新規・legacyにDB表なし）
     - UNIQUE(cart_id, item_id) で1カート内の同一itemIdの重複行を構造的に禁止（幽霊行対策・AC2）。
     - version列は付与しない（D3・上記コメント参照）。加算はON DUPLICATE KEY UPDATEで単文アトミック化。
   ============================================================ */
CREATE TABLE t_cart_item (
      cart_item_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'カート明細ID(代理キー)'
    , cart_id BIGINT UNSIGNED NOT NULL COMMENT 'カートID'
    , item_id VARCHAR(10) NOT NULL COMMENT 'アイテムID'
    , quantity INT NOT NULL COMMENT '数量(正の整数・0以下は行削除で対応)'
    -- WHO カラム標準ブロック
    , create_user_id BIGINT UNSIGNED NULL COMMENT '作成者ユーザID'
    , create_program VARCHAR(100) NOT NULL COMMENT '作成機能(ClassName#method)'
    , created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '作成日時'
    , update_user_id BIGINT UNSIGNED NULL COMMENT '更新者ユーザID'
    , update_program VARCHAR(100) NOT NULL COMMENT '更新機能(ClassName#method)'
    , updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                           ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新日時'
    , PRIMARY KEY (cart_item_id)
    , UNIQUE KEY uk_t_cart_item_cart_id_item_id (cart_id, item_id)
    , KEY idx_t_cart_item_item_id (item_id)
    , CONSTRAINT fk_t_cart_item_cart_id FOREIGN KEY (cart_id)
        REFERENCES t_cart (cart_id) ON DELETE RESTRICT ON UPDATE RESTRICT
    , CONSTRAINT fk_t_cart_item_item_id FOREIGN KEY (item_id)
        REFERENCES m_item (item_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_ja_0900_as_cs COMMENT = 'カート明細';
