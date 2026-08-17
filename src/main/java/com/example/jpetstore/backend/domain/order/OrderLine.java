package com.example.jpetstore.backend.domain.order;

import java.math.BigDecimal;

/**
 * 注文明細1行の新規作成に必要な最小の書込み用ドメインモデル（#30・O1=案A）。
 *
 * <p>{@link #lineNum} は呼び出し元（{@code OrderApplicationService}）が item_id 昇順（{@code
 * CartRepository#findByCartId} の順序＝#8 固定順）で1から採番する。{@link #unitPrice} は確定時点の {@code
 * m_item.list_price}（サーバ再計算・AC1/AC4）。
 */
public record OrderLine(String itemId, int lineNum, int quantity, BigDecimal unitPrice) {}
