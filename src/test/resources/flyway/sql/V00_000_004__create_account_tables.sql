-- ------------------------------------------------------------
-- V00_000_004__create_account_tables.sql
--   モダン版 JPetStore: アカウントドメイン（account / signon / profile）テーブル定義
--
--   移行元: legacy-jpetstore/db/mysql/jpetstore-mysql-schema.sql
--           signon / account / profile / bannerdata
--   参照: spec/behavior/account.md, spec/behavior/auth.md, spec/architecture-conventions.md §2/§4
--
--   AC2(SBD-5): signon.password はハッシュ格納可能な長さへ拡張（varchar(25)→varchar(255)）。
--   AC3: bannerdata依存を廃止（account/ログイン取得クエリのINNER JOIN依存を解消）。
--        本マイグレーションでは bannerdata テーブル自体・profile.banneropt/mylistopt 列を作らない
--        （ID-7）。favcategory は「任意のプロフィール設定」として favorite_category_id を残す
--        （バナー/MyList表示はしない）。
--   代理キー: m_account.user_id BIGINT AUTO_INCREMENT を新規導入し、legacy userid は
--             username UNIQUE 列として維持（アプリの検索キーはusername）。
--   AC7: 更新が発生するエンティティ表（account/signon/profile）に version 楽観ロック列を付与
--        （architecture-conventions §4.2）。
-- ------------------------------------------------------------

/* ============================================================
   m_account : アカウントマスタ（← legacy account）
   ============================================================ */
CREATE TABLE m_account (
      user_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ユーザID(代理キー)'
    , username VARCHAR(80) NOT NULL COMMENT 'ユーザ名(旧userid・自然キー)'
    , email VARCHAR(80) NOT NULL COMMENT 'メールアドレス'
    , first_name VARCHAR(80) NOT NULL COMMENT '名'
    , last_name VARCHAR(80) NOT NULL COMMENT '姓'
    , status VARCHAR(2) NULL COMMENT 'ステータス'
    , address1 VARCHAR(80) NOT NULL COMMENT '住所1'
    , address2 VARCHAR(40) NULL COMMENT '住所2'
    , city VARCHAR(80) NOT NULL COMMENT '市区町村'
    , state VARCHAR(80) NOT NULL COMMENT '都道府県/州'
    , postal_code VARCHAR(20) NOT NULL COMMENT '郵便番号'
    , country VARCHAR(20) NOT NULL COMMENT '国'
    , phone VARCHAR(80) NOT NULL COMMENT '電話番号'
    -- 並行制御（architecture-conventions §4.2）
    , version BIGINT NOT NULL DEFAULT 0 COMMENT '楽観ロック用バージョン'
    -- WHO カラム標準ブロック
    , create_user_id BIGINT UNSIGNED NULL COMMENT '作成者ユーザID'
    , create_program VARCHAR(100) NOT NULL COMMENT '作成機能(ClassName#method)'
    , created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '作成日時'
    , update_user_id BIGINT UNSIGNED NULL COMMENT '更新者ユーザID'
    , update_program VARCHAR(100) NOT NULL COMMENT '更新機能(ClassName#method)'
    , updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                           ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新日時'
    , PRIMARY KEY (user_id)
    , UNIQUE KEY uk_m_account_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_ja_0900_as_cs COMMENT = 'アカウントマスタ';

/* ============================================================
   m_signon : 認証情報（← legacy signon）
     - AC2(SBD-5): password_hash varchar(255)（旧 password varchar(25)＝平文）
   ============================================================ */
CREATE TABLE m_signon (
      user_id BIGINT UNSIGNED NOT NULL COMMENT 'ユーザID'
    , password_hash VARCHAR(255) NOT NULL COMMENT 'パスワードハッシュ(bcrypt/argon2等)'
    , version BIGINT NOT NULL DEFAULT 0 COMMENT '楽観ロック用バージョン'
    , create_user_id BIGINT UNSIGNED NULL COMMENT '作成者ユーザID'
    , create_program VARCHAR(100) NOT NULL COMMENT '作成機能(ClassName#method)'
    , created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '作成日時'
    , update_user_id BIGINT UNSIGNED NULL COMMENT '更新者ユーザID'
    , update_program VARCHAR(100) NOT NULL COMMENT '更新機能(ClassName#method)'
    , updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                           ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新日時'
    , PRIMARY KEY (user_id)
    , CONSTRAINT fk_m_signon_user_id FOREIGN KEY (user_id)
        REFERENCES m_account (user_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_ja_0900_as_cs COMMENT = '認証情報';

/* ============================================================
   m_profile : プロフィール（← legacy profile）
     - AC3(ID-7): banneropt/mylistopt は持たない（bannerdata依存を廃止）。
       favcategory は favorite_category_id として任意設定のみ残す。
   ============================================================ */
CREATE TABLE m_profile (
      user_id BIGINT UNSIGNED NOT NULL COMMENT 'ユーザID'
    , language_preference VARCHAR(80) NOT NULL COMMENT '言語設定(旧langpref)'
    , favorite_category_id VARCHAR(10) NULL COMMENT 'お気に入りカテゴリ(旧favcategory・任意)'
    , version BIGINT NOT NULL DEFAULT 0 COMMENT '楽観ロック用バージョン'
    , create_user_id BIGINT UNSIGNED NULL COMMENT '作成者ユーザID'
    , create_program VARCHAR(100) NOT NULL COMMENT '作成機能(ClassName#method)'
    , created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '作成日時'
    , update_user_id BIGINT UNSIGNED NULL COMMENT '更新者ユーザID'
    , update_program VARCHAR(100) NOT NULL COMMENT '更新機能(ClassName#method)'
    , updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                           ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新日時'
    , PRIMARY KEY (user_id)
    , KEY idx_m_profile_favorite_category_id (favorite_category_id)
    , CONSTRAINT fk_m_profile_user_id FOREIGN KEY (user_id)
        REFERENCES m_account (user_id) ON DELETE RESTRICT ON UPDATE RESTRICT
    , CONSTRAINT fk_m_profile_favorite_category_id FOREIGN KEY (favorite_category_id)
        REFERENCES m_category (category_id) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_ja_0900_as_cs COMMENT = 'プロフィール';
