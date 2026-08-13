-- ------------------------------------------------------------
-- V00_000_002__insert_m_code.sql
--   m_code 区分値の初期データ
--
--   ※ これは「例（サンプル）」である。ここに載せた 2 区分（OrderStatus /
--      CardType）と各コード値は雛形の動作確認用であり、実際の区分値・採番は
--      PO の Story/仕様で確定する（migration-agent-base/.claude/rules/database.md）。
--
--   INSERT テンプレート規約:
--     - es 列なし（多言語は日英のみ: display_name_ja / display_name_en）
--     - WHO は Interceptor が効かないためリテラル 'INIT_DATA' を明示
--     - code_type 0012（ProgramType）は作らない
-- ------------------------------------------------------------

-- 0001: 注文ステータス (OrderStatus) ... 例: NEW / PAID / SHIPPED
INSERT INTO m_code (
    code_type, code_type_name, code_type_name_en, code_value, name,
    display_name_ja, display_name_en,
    remarks, display_order,
    create_user_id, create_program, created_at,
    update_user_id, update_program, updated_at
) VALUES
    ('0001', '注文ステータス', 'OrderStatus', 'NEW', 'NEW',
     '新規', 'New',
     NULL, '10001',
     1, 'INIT_DATA', NOW(6),
     1, 'INIT_DATA', NOW(6)),
    ('0001', '注文ステータス', 'OrderStatus', 'PAID', 'PAID',
     '支払済', 'Paid',
     NULL, '20002',
     1, 'INIT_DATA', NOW(6),
     1, 'INIT_DATA', NOW(6)),
    ('0001', '注文ステータス', 'OrderStatus', 'SHIPPED', 'SHIPPED',
     '出荷済', 'Shipped',
     NULL, '30003',
     1, 'INIT_DATA', NOW(6),
     1, 'INIT_DATA', NOW(6));

-- 0002: カード種別 (CardType) ... 例: VISA / MASTERCARD / AMEX
INSERT INTO m_code (
    code_type, code_type_name, code_type_name_en, code_value, name,
    display_name_ja, display_name_en,
    remarks, display_order,
    create_user_id, create_program, created_at,
    update_user_id, update_program, updated_at
) VALUES
    ('0002', 'カード種別', 'CardType', 'VISA', 'VISA',
     'VISA', 'Visa',
     NULL, '10001',
     1, 'INIT_DATA', NOW(6),
     1, 'INIT_DATA', NOW(6)),
    ('0002', 'カード種別', 'CardType', 'MASTERCARD', 'MASTERCARD',
     'Mastercard', 'Mastercard',
     NULL, '20002',
     1, 'INIT_DATA', NOW(6),
     1, 'INIT_DATA', NOW(6)),
    ('0002', 'カード種別', 'CardType', 'AMEX', 'AMEX',
     'アメリカン・エキスプレス', 'American Express',
     NULL, '30003',
     1, 'INIT_DATA', NOW(6),
     1, 'INIT_DATA', NOW(6));
