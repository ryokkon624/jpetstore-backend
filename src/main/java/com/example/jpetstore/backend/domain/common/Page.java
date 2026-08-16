package com.example.jpetstore.backend.domain.common;

import java.util.List;

/**
 * 一覧APIのページング結果を表す汎用VO（#1・ユーザー承認 2026-08-16・論点3）。
 *
 * <p>1-index（{@code page} は1始まり）。Application Serviceが返しPresentation層がそのまま応答本文として使う横断的な型のため、
 * 個々のServiceの内部クラスにはせず {@code domain.common} に置く（#2/#9 が再利用する先例）。
 */
public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

  public Page {
    content = List.copyOf(content);
  }

  /**
   * 1-indexの{@code page}・{@code size}・totalElementsから{@link Page}を組み立てる。totalPagesは {@code
   * Math.ceil(totalElements / size)}（0件なら0頁）。
   */
  public static <T> Page<T> of(List<T> content, int page, int size, long totalElements) {
    int totalPages = size <= 0 ? 0 : (int) Math.ceil(totalElements / (double) size);
    return new Page<>(content, page, size, totalElements, totalPages);
  }
}
