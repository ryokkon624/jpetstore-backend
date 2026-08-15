package com.example.jpetstore.backend.domain.catalog;

/** カテゴリの参照系ドメインモデル（#1・{@code m_category} 1件）。 */
public record Category(String categoryId, String name, String description) {}
