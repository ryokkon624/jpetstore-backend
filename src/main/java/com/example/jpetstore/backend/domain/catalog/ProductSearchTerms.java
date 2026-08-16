package com.example.jpetstore.backend.domain.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * 検索キーワードを語分割し、LIKE用にエスケープ済みパターンへ変換する純VO（#2 AC1/[L2]/ID-29）。
 *
 * <p>語分割は as-is（legacy {@code SqlMapProductDao.ProductSearch}）と同じ単一スペース区切り（{@link
 * StringTokenizer}）を踏襲する（[L2] 旧同値）。各語は LIKE メタ文字（{@code %}/{@code _}/{@code \}）を
 * バックスラッシュでエスケープしリテラル化してから {@code %}...{@code %} で囲む（ID-29・ハードニング。SQL 側は {@code ESCAPE '\\'}
 * を併用する前提）。
 *
 * <p>このVOは文字列operatingのみを行いSQL文字列そのものは組み立てない（呼び出し側で {@code #{}} パラメタライズする）ため、 SQLi（AC-neg1）は成立しない。
 */
public record ProductSearchTerms(List<String> patterns) {

  public ProductSearchTerms {
    patterns = List.copyOf(patterns);
  }

  /** null・空文字・空白のみのキーワードは空パターン（{@link #isEmpty()}）になる（AC2・空結果への正規化に使う）。 */
  public static ProductSearchTerms from(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return new ProductSearchTerms(List.of());
    }
    List<String> result = new ArrayList<>();
    StringTokenizer tokenizer = new StringTokenizer(keyword, " ", false);
    while (tokenizer.hasMoreTokens()) {
      result.add("%" + escapeLikeMetacharacters(tokenizer.nextToken()) + "%");
    }
    return new ProductSearchTerms(result);
  }

  public boolean isEmpty() {
    return patterns.isEmpty();
  }

  /** LIKE メタ文字（{@code \}・{@code %}・{@code _}）をバックスラッシュでエスケープしリテラル化する（ID-29）。 */
  private static String escapeLikeMetacharacters(String token) {
    StringBuilder escaped = new StringBuilder(token.length());
    for (int i = 0; i < token.length(); i++) {
      char c = token.charAt(i);
      if (c == '\\' || c == '%' || c == '_') {
        escaped.append('\\');
      }
      escaped.append(c);
    }
    return escaped.toString();
  }
}
