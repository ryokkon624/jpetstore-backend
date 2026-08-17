package com.example.jpetstore.backend.domain.cart;

/**
 * 在庫コンテキストVO（#29・{@code CartRepository#findStock} の戻り値）。
 *
 * <p>{@code m_item}×{@code t_inventory}×{@code t_cart_item}
 * から解決した「アイテムの在庫qty・当該カート内の既存数量」を表す。{@code Cart}/{@code CartItem} のコマンドメソッド（{@code addItem}/{@code
 * updateItem}/{@code mergeLine}）が不変条件（在庫上限・加算オーバーフロー・merge
 * クランプ）を判定するための入力専用であり、Application/Presentation層より上へそのまま渡してはならない（在庫数非露出・ID-28）。
 */
public record StockAvailability(String itemId, int stockQuantity, int currentQuantity) {}
