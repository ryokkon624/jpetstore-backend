package com.example.jpetstore.backend.application.service;

import com.example.jpetstore.backend.domain.catalog.CatalogRepository;
import com.example.jpetstore.backend.domain.catalog.Category;
import com.example.jpetstore.backend.domain.catalog.ItemDetail;
import com.example.jpetstore.backend.domain.catalog.ItemStock;
import com.example.jpetstore.backend.domain.catalog.ItemSummary;
import com.example.jpetstore.backend.domain.catalog.OrderabilityResult;
import com.example.jpetstore.backend.domain.catalog.Product;
import com.example.jpetstore.backend.domain.catalog.ProductSearchTerms;
import com.example.jpetstore.backend.domain.common.Page;
import com.example.jpetstore.backend.domain.common.PageRequest;
import com.example.jpetstore.backend.domain.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * カタログ階層閲覧のユースケース（#1 AC1/AC2/AC3/AC4）。
 *
 * <p>読み取り専用。category一覧は非ページング（[L2]で5件固定）、product/item一覧はサーバサイドページング（論点3・1-index・既定size=12・cap=100）。
 *
 * <p>AC3（在庫数非露出）: 生の在庫数量（qty）は {@link CatalogRepository} 実装内（{@code StockStatusCalculator#of}）で
 * {@code StockStatus} に変換済みの値のみを受け取り、qty自体をこのクラスの戻り値（Domainモデル）に含めない。{@link #checkOrderable}
 * のみ、閾値判定に必要なraw値を {@link ItemStock} 経由で受け取るが、応答（{@link OrderabilityResult}）には含めない。
 *
 * <p>#30: {@code CatalogCustomMapper} 直呼びから {@link CatalogRepository}（Domain層）経由へ retrofit
 * した（{@code backend-conventions} §9）。
 */
@Service
public class CatalogApplicationService {

  private final CatalogRepository catalogRepository;

  public CatalogApplicationService(CatalogRepository catalogRepository) {
    this.catalogRepository = catalogRepository;
  }

  /** カテゴリ一覧（[L2]で5件固定・非ページング）。 */
  public List<Category> listCategories() {
    return catalogRepository.listCategories();
  }

  /** カテゴリ詳細。存在しなければ {@link ResourceNotFoundException}（→404）。 */
  public Category getCategory(String categoryId) {
    return catalogRepository
        .findCategoryById(categoryId)
        .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
  }

  /** カテゴリ内の商品一覧（ページング）。カテゴリ自体が存在しなければ {@link ResourceNotFoundException}（→404）。 */
  public Page<Product> listProductsByCategory(String categoryId, Integer page, Integer size) {
    if (catalogRepository.findCategoryById(categoryId).isEmpty()) {
      throw new ResourceNotFoundException("Category not found: " + categoryId);
    }
    PageRequest pageRequest = PageRequest.of(page, size);
    List<Product> content =
        catalogRepository.listProductsByCategoryId(
            categoryId, pageRequest.offset(), pageRequest.size());
    long totalElements = catalogRepository.countProductsByCategoryId(categoryId);
    return Page.of(content, pageRequest.page(), pageRequest.size(), totalElements);
  }

  /** 商品詳細。存在しなければ {@link ResourceNotFoundException}（→404）。 */
  public Product getProduct(String productId) {
    return catalogRepository
        .findProductById(productId)
        .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
  }

  /** 商品内の在庫アイテム一覧（ページング）。商品自体が存在しなければ {@link ResourceNotFoundException}（→404）。 */
  public Page<ItemSummary> listItemsByProduct(String productId, Integer page, Integer size) {
    if (catalogRepository.findProductById(productId).isEmpty()) {
      throw new ResourceNotFoundException("Product not found: " + productId);
    }
    PageRequest pageRequest = PageRequest.of(page, size);
    List<ItemSummary> content =
        catalogRepository.listItemsByProductId(productId, pageRequest.offset(), pageRequest.size());
    long totalElements = catalogRepository.countItemsByProductId(productId);
    return Page.of(content, pageRequest.page(), pageRequest.size(), totalElements);
  }

  /**
   * 商品検索（#2 AC1/AC2/AC3・ID-29）。キーワードを {@link ProductSearchTerms} で語分割・LIKEエスケープし、
   * name/category_id/description へのOR一致（パターン間もOR）で検索する（legacy 挙動踏襲・[L2]）。
   *
   * <p>AC2: null・空文字・空白のみのキーワードは（500やtraceを出さず）空結果へ正規化する。{@code categoryId} は任意で、 指定時はその配下に限定する。
   */
  public Page<Product> searchProducts(
      String keyword, String categoryId, Integer page, Integer size) {
    PageRequest pageRequest = PageRequest.of(page, size);
    ProductSearchTerms terms = ProductSearchTerms.from(keyword);
    if (terms.isEmpty()) {
      return Page.of(List.of(), pageRequest.page(), pageRequest.size(), 0);
    }
    String normalizedCategoryId = (categoryId == null || categoryId.isBlank()) ? null : categoryId;
    List<Product> content =
        catalogRepository.searchProducts(
            terms.patterns(), normalizedCategoryId, pageRequest.offset(), pageRequest.size());
    long totalElements =
        catalogRepository.countSearchProducts(terms.patterns(), normalizedCategoryId);
    return Page.of(content, pageRequest.page(), pageRequest.size(), totalElements);
  }

  /** アイテム詳細（在庫status・qty非露出）。存在しなければ {@link ResourceNotFoundException}（→404）。 */
  public ItemDetail getItem(String itemId) {
    return catalogRepository
        .findItemDetailById(itemId)
        .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemId));
  }

  /**
   * 指定数量でアイテムを注文可能か判定する（#4 D1）。未ログインでも呼べる公開EPの実体。在庫数そのものは返さず、orderable真偽＋安定した理由コードのみ返す（ID-28）。
   *
   * @throws ResourceNotFoundException 存在しない itemId（AC4/SBD-10）
   */
  public OrderabilityResult checkOrderable(String itemId, int quantity) {
    ItemStock stock =
        catalogRepository
            .findItemStockById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemId));
    if (quantity <= 0) {
      return OrderabilityResult.notOrderable(OrderabilityResult.REASON_INVALID_QUANTITY);
    }
    if (stock.stockQuantity() <= 0) {
      return OrderabilityResult.notOrderable(OrderabilityResult.REASON_OUT_OF_STOCK);
    }
    if (quantity > stock.stockQuantity()) {
      return OrderabilityResult.notOrderable(OrderabilityResult.REASON_EXCEEDS_STOCK);
    }
    return OrderabilityResult.ok();
  }
}
