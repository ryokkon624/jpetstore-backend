package com.example.jpetstore.backend.presentation.rest.dto;

import com.example.jpetstore.backend.domain.common.Page;
import java.util.List;
import java.util.function.Function;

/**
 * 一覧APIのページングレスポンス（#1・ユーザー承認 2026-08-16・論点3）。
 *
 * <p>1-index（{@code page} は1始まり）。#2（商品検索）・#9（注文履歴一覧）が再利用する先例規約のため、個々のControllerに閉じず {@code
 * presentation.rest.dto} に置く。
 */
public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages) {

  /**
   * Domain層の {@link Page} を、要素ごとの {@code mapper} でPresentation DTOへ変換して {@link PageResponse}
   * を組み立てる。
   */
  public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
    return new PageResponse<>(
        page.content().stream().map(mapper).toList(),
        page.page(),
        page.size(),
        page.totalElements(),
        page.totalPages());
  }
}
