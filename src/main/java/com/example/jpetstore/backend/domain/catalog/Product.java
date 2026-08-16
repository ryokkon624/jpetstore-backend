package com.example.jpetstore.backend.domain.catalog;

/** 商品の参照系ドメインモデル（#1・{@code m_product} 1件）。 */
public record Product(String productId, String categoryId, String name, String description) {}
