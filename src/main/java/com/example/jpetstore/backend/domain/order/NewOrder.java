package com.example.jpetstore.backend.domain.order;

import com.example.jpetstore.backend.domain.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 注文ヘッダの新規作成に必要な最小の書込み用ドメインモデル（#30・O1=案A）。
 *
 * <p>rich な Order 集約（{@code reconstruct()}＋不変条件メソッド）は作らない。#8 の原子的引当（item_id昇順固定順・ all-or-nothing）は
 * persistence/tx の関心事として {@code OrderApplicationService} に残し、本レコードは {@link
 * com.example.jpetstore.backend.domain.order.OrderRepository#insertHeader} への入力を表す薄いVOに留める。
 *
 * <p>{@link #shipping} は呼び出し元（{@code OrderApplicationService}）が {@link
 * PlaceOrderCommand#effectiveShipping()} で解決済みの実配送先（生の {@code useSeparateShipping} 分岐結果）を渡す。
 */
public record NewOrder(
    Long userId,
    OrderAddress billing,
    OrderAddress shipping,
    BigDecimal totalPrice,
    OrderStatus status,
    LocalDate orderDate) {}
