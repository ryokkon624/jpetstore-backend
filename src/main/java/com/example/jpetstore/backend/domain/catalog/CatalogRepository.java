package com.example.jpetstore.backend.domain.catalog;

import java.util.List;
import java.util.Optional;

/**
 * カタログ（category → product → item ＋ inventory）の永続化アクセスの唯一の入口（#30・{@code backend-conventions}
 * §1/§2/§9）。
 *
 * <p>実装は Infrastructure 層（{@code
 * infrastructure.mybatis.catalog.MyBatisCatalogRepository}）に置く（依存性逆転）。 Application 層（{@code
 * CatalogApplicationService}）はこのインターフェイスのみに依存し、MyBatis の {@code CatalogCustomMapper} や {@code
 * *CustomEntity} を直接扱わない。
 *
 * <p>読み取り専用の CQRS 射影として扱う（{@code backend-conventions} §9）。read-model の record（{@link
 * Category}/{@link Product}/{@link ItemSummary}/{@link ItemDetail}）をそのまま返し、集約の {@code
 * reconstruct()} 再構築は強制しない。 {@code StockStatus} 射影を含む Entity→record 変換は Repository 実装内に閉じる。
 */
public interface CatalogRepository {

  /** カテゴリ一覧（[L2]で5件固定・非ページング）。 */
  List<Category> listCategories();

  /** カテゴリを1件取得する。 */
  Optional<Category> findCategoryById(String categoryId);

  /** カテゴリ内の商品一覧（ページング）。 */
  List<Product> listProductsByCategoryId(String categoryId, int offset, int limit);

  /** カテゴリ内の商品件数。 */
  long countProductsByCategoryId(String categoryId);

  /** 商品を1件取得する。 */
  Optional<Product> findProductById(String productId);

  /** 商品内の在庫アイテム一覧（ページング）。 */
  List<ItemSummary> listItemsByProductId(String productId, int offset, int limit);

  /** 商品内の在庫アイテム件数。 */
  long countItemsByProductId(String productId);

  /**
   * 商品検索（#2 AC1/AC3・ID-29）。{@code patterns} は呼び出し元（{@code ProductSearchTerms}）が語分割・LIKEエスケープ済みの
   * {@code %...%} パターン列（空であってはならない）。
   */
  List<Product> searchProducts(List<String> patterns, String categoryId, int offset, int limit);

  /** 商品検索の総件数。 */
  long countSearchProducts(List<String> patterns, String categoryId);

  /** アイテム詳細（在庫status・qty非露出）を1件取得する。 */
  Optional<ItemDetail> findItemDetailById(String itemId);

  /** アイテムの生在庫数量（{@code checkOrderable} の不変条件判定専用）を1件取得する。 */
  Optional<ItemStock> findItemStockById(String itemId);
}
