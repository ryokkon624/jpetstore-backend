package com.example.jpetstore.backend.domain.order;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 注文詳細閲覧（#10）で所有者解決と応答の両方に使う注文ヘッダの読取専用ドメインモデル。
 *
 * <p>{@link #userId} は {@code OrderApplicationService#getOrder} が {@code
 * OwnershipAuthorizationService#assertOwner} に渡す「サーバー側で解決した真の所有者」として使う（SBD-1）。 header
 * 読取は1回のみ行い、識別子解決用と最終応答用を分けない（Sprint12/13の教訓を踏襲・perf純増を避ける）。
 */
public record OrderHeader(Long orderId, Long userId, LocalDate orderDate, BigDecimal totalPrice) {}
