-- ------------------------------------------------------------
-- V00_000_013__add_m_profile_color_scheme_preference.sql
--   モダン版 JPetStore: テーマ配色設定（system/light/dark）を永続化する列を追加
--   （legacy には存在しない純新規 UX。#36 参照）。
--
--   参照: backlog/sprint_19/sprint_backlog.md #36 AC8。
--
--   #36 AC5/AC6/AC8: ログイン済みユーザーの配色選択を AccountEdit 保存（PUT /api/account）時に
--   永続化し、ログイン/再水和（/api/auth/me）で DB 権威として適用する。
--   NOT NULL DEFAULT 'system' とすることで、既存行（本マイグレーション適用前に作成済みの
--   m_profile 行）は自動的に 'system' で埋まり、後続の INSERT でも本列を明示しない限り
--   'system' が既定値になる（register は backend 無変更で自動充足。A3）。
--
--   VARCHAR(20)（'system'/'light'/'dark' の3値・enum固定）とし、backend側の
--   Bean Validation（@Pattern(regexp="^(system|light|dark)$")）と桁数を揃える。
-- ------------------------------------------------------------

ALTER TABLE m_profile
    ADD COLUMN color_scheme_preference VARCHAR(20) NOT NULL DEFAULT 'system'
        COMMENT 'テーマ配色設定(system/light/dark)' AFTER language_preference;
