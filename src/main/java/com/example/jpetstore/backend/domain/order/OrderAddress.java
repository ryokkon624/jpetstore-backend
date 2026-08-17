package com.example.jpetstore.backend.domain.order;

/**
 * 注文確定時の配送/請求先1件分（#8 AC1）。
 *
 * <p>{@code t_order} の永続列（{@code ship_*}/{@code bill_*}/{@code *_to_first_name}/{@code
 * *_to_last_name}）に対応するフィールドのみを持つ。frontend の {@code Address}（email/phone を含む）とは異なり、 email/phone は
 * {@code t_order} に列が無く非永続のため本レコードには含めない（計画フェーズ確定・F3.6）。
 */
public record OrderAddress(
    String firstName,
    String lastName,
    String address1,
    String address2,
    String city,
    String state,
    String postalCode,
    String country) {}
