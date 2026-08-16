-- ------------------------------------------------------------
-- V00_000_008__insert_catalog_master.sql
--   モダン版 JPetStore: カタログドメイン（商品階層）マスタデータ
--
--   移行元: legacy-jpetstore/db/mysql/jpetstore-mysql-dataload.sql
--           supplier / category / product / item / inventory
--   参照: spec/behavior/catalog.md, migration-agent-base/backlog/sprint_06/sprint_backlog.md #1
--
--   #1 AC1/[L2]: レガシーの階層データ（category5 → product16 → item28）に忠実に投入する
--   （categoryX→商品件数・productY→item一覧が旧同値）。
--   #1 AC5(SBD-18/ID-15): description は plaintext（レガシーの `<image src=...>` 埋め込みHTMLを
--   継承しない。承認済の非等価変更）。商品/カテゴリ画像は spec/design/images の新規アセットを使う
--   （frontend側で取り込み。DBにはHTML/画像パスを持たせない）。
--   #1 AC3: t_inventory.quantity を在庫3状態（在庫あり/残少/在庫切れ・N=5）が出るように作り分ける。
--   本番相当のマスタデータのため flyway/sql（m_code seed=V00_000_002と同格）に配置する。
--   WHO列: Interceptorが効かないためリテラル 'INIT_DATA' を明示する。
--   FK順: m_supplier → m_category → m_product → m_item → t_inventory。
-- ------------------------------------------------------------

/* ============================================================
   m_supplier（← legacy supplier）
   ============================================================ */
INSERT INTO m_supplier (
    supplier_id, name, status, address1, address2, city, state, postal_code, phone,
    create_user_id, create_program, created_at, update_user_id, update_program, updated_at
) VALUES
    (1, 'XYZ Pets', 'AC', '600 Avon Way', NULL, 'Los Angeles', 'CA', '94024', '212-947-0797',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    (2, 'ABC Pets', 'AC', '700 Abalone Way', NULL, 'San Francisco', 'CA', '94024', '415-947-0797',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6));

/* ============================================================
   m_category（← legacy category。descriptionはplaintext・HTML非継承）
   ============================================================ */
INSERT INTO m_category (
    category_id, name, description,
    create_user_id, create_program, created_at, update_user_id, update_program, updated_at
) VALUES
    ('FISH', 'Fish', 'A wide variety of fish for your aquarium.',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('DOGS', 'Dogs', 'Friendly, loyal dogs for every family.',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('REPTILES', 'Reptiles', 'Unique reptiles for the adventurous owner.',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('CATS', 'Cats', 'Independent, affectionate cats.',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('BIRDS', 'Birds', 'Colorful, cheerful birds.',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6));

/* ============================================================
   m_product（← legacy product。descriptionはplaintext・HTML非継承）
   ============================================================ */
INSERT INTO m_product (
    product_id, category_id, name, description,
    create_user_id, create_program, created_at, update_user_id, update_program, updated_at
) VALUES
    ('FI-SW-01', 'FISH', 'Angelfish', 'Salt Water fish from Australia',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('FI-SW-02', 'FISH', 'Tiger Shark', 'Salt Water fish from Australia',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('FI-FW-01', 'FISH', 'Koi', 'Fresh Water fish from Japan',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('FI-FW-02', 'FISH', 'Goldfish', 'Fresh Water fish from China',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('K9-BD-01', 'DOGS', 'Bulldog', 'Friendly dog from England',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('K9-PO-02', 'DOGS', 'Poodle', 'Cute dog from France',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('K9-DL-01', 'DOGS', 'Dalmation', 'Great dog for a Fire Station',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('K9-RT-01', 'DOGS', 'Golden Retriever', 'Great family dog',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('K9-RT-02', 'DOGS', 'Labrador Retriever', 'Great hunting dog',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('K9-CW-01', 'DOGS', 'Chihuahua', 'Great companion dog',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('RP-SN-01', 'REPTILES', 'Rattlesnake', 'Doubles as a watch dog',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('RP-LI-02', 'REPTILES', 'Iguana', 'Friendly green friend',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('FL-DSH-01', 'CATS', 'Manx', 'Great for reducing mouse populations',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('FL-DLH-02', 'CATS', 'Persian', 'Friendly house cat, doubles as a princess',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('AV-CB-01', 'BIRDS', 'Amazon Parrot', 'Great companion for up to 75 years',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('AV-SB-02', 'BIRDS', 'Finch', 'Great stress reliever',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6));

/* ============================================================
   m_item（← legacy item。status='P'=有効。attribute1のみ使用）
   ============================================================ */
INSERT INTO m_item (
    item_id, product_id, list_price, unit_cost, supplier_id, status, attribute1,
    create_user_id, create_program, created_at, update_user_id, update_program, updated_at
) VALUES
    ('EST-1', 'FI-SW-01', 16.50, 10.00, 1, 'P', 'Large',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-2', 'FI-SW-01', 16.50, 10.00, 1, 'P', 'Small',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-3', 'FI-SW-02', 18.50, 12.00, 1, 'P', 'Toothless',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-4', 'FI-FW-01', 18.50, 12.00, 1, 'P', 'Spotted',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-5', 'FI-FW-01', 18.50, 12.00, 1, 'P', 'Spotless',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-6', 'K9-BD-01', 18.50, 12.00, 1, 'P', 'Male Adult',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-7', 'K9-BD-01', 18.50, 12.00, 1, 'P', 'Female Puppy',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-8', 'K9-PO-02', 18.50, 12.00, 1, 'P', 'Male Puppy',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-9', 'K9-DL-01', 18.50, 12.00, 1, 'P', 'Spotless Male Puppy',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-10', 'K9-DL-01', 18.50, 12.00, 1, 'P', 'Spotted Adult Female',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-11', 'RP-SN-01', 18.50, 12.00, 1, 'P', 'Venomless',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-12', 'RP-SN-01', 18.50, 12.00, 1, 'P', 'Rattleless',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-13', 'RP-LI-02', 18.50, 12.00, 1, 'P', 'Green Adult',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-14', 'FL-DSH-01', 58.50, 12.00, 1, 'P', 'Tailless',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-15', 'FL-DSH-01', 23.50, 12.00, 1, 'P', 'With tail',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-16', 'FL-DLH-02', 93.50, 12.00, 1, 'P', 'Adult Female',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-17', 'FL-DLH-02', 93.50, 12.00, 1, 'P', 'Adult Male',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-18', 'AV-CB-01', 193.50, 92.00, 1, 'P', 'Adult Male',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-19', 'AV-SB-02', 15.50, 2.00, 1, 'P', 'Adult Male',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-20', 'FI-FW-02', 5.50, 2.00, 1, 'P', 'Adult Male',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-21', 'FI-FW-02', 5.29, 1.00, 1, 'P', 'Adult Female',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-22', 'K9-RT-02', 135.50, 100.00, 1, 'P', 'Adult Male',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-23', 'K9-RT-02', 145.49, 100.00, 1, 'P', 'Adult Female',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-24', 'K9-RT-02', 255.50, 92.00, 1, 'P', 'Adult Male',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-25', 'K9-RT-02', 325.29, 90.00, 1, 'P', 'Adult Female',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-26', 'K9-CW-01', 125.50, 92.00, 1, 'P', 'Adult Male',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-27', 'K9-CW-01', 155.29, 90.00, 1, 'P', 'Adult Female',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-28', 'K9-RT-01', 155.29, 90.00, 1, 'P', 'Adult Female',
     1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6));

/* ============================================================
   t_inventory（← legacy inventory。#1 AC3: 在庫3状態を作り分ける（N=5）
     - 在庫切れ(qty<=0): EST-3, EST-21 (2件)
     - 残少(0<qty<=5)  : EST-2, EST-9, EST-15 (3件)
     - 在庫あり(qty>5) : 上記以外 (23件)
   ============================================================ */
INSERT INTO t_inventory (
    item_id, quantity,
    create_user_id, create_program, created_at, update_user_id, update_program, updated_at
) VALUES
    ('EST-1', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-2', 1, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-3', 0, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-4', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-5', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-6', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-7', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-8', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-9', 3, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-10', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-11', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-12', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-13', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-14', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-15', 5, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-16', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-17', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-18', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-19', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-20', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-21', 0, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-22', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-23', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-24', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-25', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-26', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-27', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6)),
    ('EST-28', 100, 1, 'INIT_DATA', NOW(6), 1, 'INIT_DATA', NOW(6));
