package com.example.jpetstore.backend.presentation.rest;

import com.example.jpetstore.backend.application.service.CatalogApplicationService;
import com.example.jpetstore.backend.domain.catalog.Category;
import com.example.jpetstore.backend.domain.catalog.ItemDetail;
import com.example.jpetstore.backend.domain.catalog.ItemSummary;
import com.example.jpetstore.backend.domain.catalog.Product;
import com.example.jpetstore.backend.presentation.rest.dto.PageResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * カタログ階層閲覧API（#1 AC1〜AC5）。
 *
 * <p>読み取り専用（GETのみ）・全公開（{@code SecurityConfig} で {@code permitAll}。未認証到達可＝AC4）。
 * category一覧は非ページング（[L2]で5件固定）、 product/item一覧はページング（AC2/ID-20・1-index・既定size=12・cap=100）。
 * item詳細の在庫状況は {@code StockStatus} の code 文字列で返し、qty自体は一切含めない（AC3・在庫数非露出）。
 *
 * <p>存在しない category/product/item は {@code CatalogApplicationService} が投げる {@link
 * com.example.jpetstore.backend.domain.exception.ResourceNotFoundException} を {@code
 * GlobalExceptionHandler} が404に正規化する（論点5・#1最小範囲。不正フォーマットID等の集約ハードニングは#3）。
 */
@RestController
@RequestMapping("/api")
public class CatalogController {

  private final CatalogApplicationService catalogApplicationService;

  public CatalogController(CatalogApplicationService catalogApplicationService) {
    this.catalogApplicationService = catalogApplicationService;
  }

  @GetMapping("/categories")
  public List<CategoryResponse> listCategories() {
    return catalogApplicationService.listCategories().stream().map(CategoryResponse::from).toList();
  }

  @GetMapping("/categories/{categoryId}")
  public CategoryResponse getCategory(@PathVariable String categoryId) {
    return CategoryResponse.from(catalogApplicationService.getCategory(categoryId));
  }

  @GetMapping("/categories/{categoryId}/products")
  public PageResponse<ProductResponse> listProductsByCategory(
      @PathVariable String categoryId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return PageResponse.from(
        catalogApplicationService.listProductsByCategory(categoryId, page, size),
        ProductResponse::from);
  }

  /**
   * 商品検索（#2 AC1/AC2/AC3・ID-29）。{@code keyword} は空でも400にせず空結果200へ正規化する（AC2）。 {@code categoryId}
   * は任意（指定時はその配下に限定）。静的パス {@code /products/search} は {@code /products/{productId}}
   * より具体的なパターンとして優先解決される（Spring MVC標準の優先順位）。
   */
  @GetMapping("/products/search")
  public PageResponse<ProductResponse> searchProducts(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String categoryId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return PageResponse.from(
        catalogApplicationService.searchProducts(keyword, categoryId, page, size),
        ProductResponse::from);
  }

  @GetMapping("/products/{productId}")
  public ProductResponse getProduct(@PathVariable String productId) {
    return ProductResponse.from(catalogApplicationService.getProduct(productId));
  }

  @GetMapping("/products/{productId}/items")
  public PageResponse<ItemSummaryResponse> listItemsByProduct(
      @PathVariable String productId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return PageResponse.from(
        catalogApplicationService.listItemsByProduct(productId, page, size),
        ItemSummaryResponse::from);
  }

  @GetMapping("/items/{itemId}")
  public ItemDetailResponse getItem(@PathVariable String itemId) {
    return ItemDetailResponse.from(catalogApplicationService.getItem(itemId));
  }

  /** カテゴリ応答DTO（Controller層に閉じる）。 */
  public record CategoryResponse(String categoryId, String name, String description) {
    static CategoryResponse from(Category category) {
      return new CategoryResponse(category.categoryId(), category.name(), category.description());
    }
  }

  /** 商品応答DTO（Controller層に閉じる）。 */
  public record ProductResponse(
      String productId, String categoryId, String name, String description) {
    static ProductResponse from(Product product) {
      return new ProductResponse(
          product.productId(), product.categoryId(), product.name(), product.description());
    }
  }

  /** 在庫アイテム一覧応答DTO（Controller層に閉じる）。qty自体は含めない（AC3）。 */
  public record ItemSummaryResponse(
      String itemId,
      String productId,
      String attribute1,
      BigDecimal listPrice,
      String stockStatus) {
    static ItemSummaryResponse from(ItemSummary item) {
      return new ItemSummaryResponse(
          item.itemId(),
          item.productId(),
          item.attribute1(),
          item.listPrice(),
          item.stockStatus().getCode());
    }
  }

  /** アイテム詳細応答DTO（Controller層に閉じる）。qty自体は含めない（AC3）。 */
  public record ItemDetailResponse(
      String itemId,
      String productId,
      String productName,
      String productDescription,
      String attribute1,
      BigDecimal listPrice,
      String stockStatus) {
    static ItemDetailResponse from(ItemDetail item) {
      return new ItemDetailResponse(
          item.itemId(),
          item.productId(),
          item.productName(),
          item.productDescription(),
          item.attribute1(),
          item.listPrice(),
          item.stockStatus().getCode());
    }
  }
}
