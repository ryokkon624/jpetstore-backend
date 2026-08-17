-- ------------------------------------------------------------
-- V00_000_011__add_t_order_user_order_index.sql
--   t_order に (user_id, order_id) 複合索引を追加する。
--
--   背景: #9 注文履歴一覧（GET /api/orders）は本人スコープ（WHERE user_id=?）で
--   ORDER BY order_id DESC のサーバページングを行う。既存の単一列索引
--   idx_t_order_user_id (user_id) だけでは user_id 絞り込み後に order_id で
--   別途ソートが必要になるため、(user_id, order_id) の複合索引で絞り込み＋
--   ソートを1索引でまかなう。
--
--   置換方針（重複索引を残さない）: 複合索引の左端prefixは単一列索引
--   idx_t_order_user_id (user_id) と同じ検索能力を持つため、既存の単一列索引は
--   同一ALTER文でDROPする。FK fk_t_order_user_id は複合索引の左端prefixで
--   引き続きバッキングされる（MySQLの仕様上、FK制約は左端prefixが一致する
--   索引があればそれを利用でき、専用の単一列索引を別途必要としない）。
--
--   参照: backlog/sprint_14/sprint_backlog.md Q1（AskUserQuestion確定・2026-08-17）
-- ------------------------------------------------------------

ALTER TABLE t_order
    ADD INDEX idx_t_order_user_id_order_id (user_id, order_id),
    DROP INDEX idx_t_order_user_id;
