package com.example.jpetstore.backend.domain.catalog;

/**
 * アイテムの生在庫数量を保持する、注文可否判定専用の参照モデル（#30・{@code checkOrderable} の不変条件判定に閉じる）。
 *
 * <p>{@link #stockQuantity} はraw値であり、{@link ItemDetail}/{@link ItemSummary} の {@code StockStatus}
 * 射影とは異なり Application層より上位（Presentation応答）へは一切露出しない（ID-28・在庫数非露出。{@code
 * CatalogApplicationService#checkOrderable} 内でのみ閾値比較に使う）。
 */
public record ItemStock(String itemId, int stockQuantity) {}
