package com.example.jpetstore.backend.domain.order;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 注文履歴一覧（#9 AC1）の1行を表す読取専用ドメインモデル。
 *
 * <p>一覧は本人スコープ（{@code WHERE user_id = principalUserId}・SBD-1）・{@code ORDER BY order_id DESC}
 * のサーバページングで返す（Sprint 14 確定）。明細・住所は持たない（一覧に不要な過剰情報を含めない）。
 */
public record OrderSummary(Long orderId, LocalDate orderDate, BigDecimal totalPrice) {}
