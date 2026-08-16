-- ------------------------------------------------------------
-- V00_000_009__insert_stock_status_code.sql
--   m_code 区分値: 在庫ステータス (StockStatus)
--
--   #1 論点2/②（ユーザー承認・2026-08-16）: 在庫バッジの状態（在庫あり/残少/在庫切れ）は
--   手書きenumではなくm_code区分値として登録する。閾値算出(qty->status)自体は
--   backend側の非生成クラス StockStatusCalculator が担う（N=5固定・格納列は持たない）。
--   code_type採番: 0001=OrderStatus, 0002=CardType の次番として0003を採用
--   （0012=ProgramTypeは廃止済のため使わない）。
--
--   code_value は m_code.code_value が VARCHAR(10) のため、OUT_OF_STOCK(12文字)ではなく
--   OUT_STOCK(9文字)を採用する（IN_STOCK=8文字・LOW_STOCK=9文字も上限内）。
-- ------------------------------------------------------------

INSERT INTO m_code (
    code_type, code_type_name, code_type_name_en, code_value, name,
    display_name_ja, display_name_en,
    remarks, display_order,
    create_user_id, create_program, created_at,
    update_user_id, update_program, updated_at
) VALUES
    ('0003', '在庫ステータス', 'StockStatus', 'IN_STOCK', 'IN_STOCK',
     '在庫あり', 'In Stock',
     NULL, '10001',
     1, 'INIT_DATA', NOW(6),
     1, 'INIT_DATA', NOW(6)),
    ('0003', '在庫ステータス', 'StockStatus', 'LOW_STOCK', 'LOW_STOCK',
     '残少', 'Low Stock',
     NULL, '20002',
     1, 'INIT_DATA', NOW(6),
     1, 'INIT_DATA', NOW(6)),
    ('0003', '在庫ステータス', 'StockStatus', 'OUT_STOCK', 'OUT_STOCK',
     '在庫切れ', 'Out of Stock',
     NULL, '30003',
     1, 'INIT_DATA', NOW(6),
     1, 'INIT_DATA', NOW(6));
