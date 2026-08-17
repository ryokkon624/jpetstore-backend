package com.example.jpetstore.backend.domain.order;

import java.math.BigDecimal;

/**
 * 注文確定の結果（#8 AC1）。最小の完了画面（注文番号＋サーバ再計算合計のみ・計画フェーズ確定②）が 必要とする値のみを持つ。明細・商品名は
 * #10（注文詳細閲覧）のスコープのためここでは扱わない。
 */
public record OrderConfirmation(Long orderId, BigDecimal totalPrice) {}
