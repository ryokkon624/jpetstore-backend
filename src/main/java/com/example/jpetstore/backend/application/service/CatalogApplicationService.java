package com.example.jpetstore.backend.application.service;

import com.example.jpetstore.backend.domain.catalog.Category;
import com.example.jpetstore.backend.domain.catalog.ItemDetail;
import com.example.jpetstore.backend.domain.catalog.ItemSummary;
import com.example.jpetstore.backend.domain.catalog.Product;
import com.example.jpetstore.backend.domain.catalog.StockStatusCalculator;
import com.example.jpetstore.backend.domain.common.Page;
import com.example.jpetstore.backend.domain.common.PageRequest;
import com.example.jpetstore.backend.domain.exception.ResourceNotFoundException;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CategoryCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ItemDetailCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ItemSummaryCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ProductCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.CatalogCustomMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * カタログ階層閲覧のユースケース（#1 AC1/AC2/AC3/AC4）。
 *
 * <p>読み取り専用。category一覧は非ページング（[L2]で5件固定）、product/item一覧はサーバサイドページング（論点3・1-index・既定size=12・cap=100）。
 *
 * <p>AC3（在庫数非露出）: {@link ItemSummaryCustomEntity#getQuantity()}/{@link
 * ItemDetailCustomEntity#getQuantity()} はこのクラス内でのみ {@link StockStatusCalculator#of} により {@code
 * StockStatus} に変換し、qty自体をこのクラスの戻り値（Domainモデル）に含めない。
 */
@Service
public class CatalogApplicationService {

  private final CatalogCustomMapper catalogCustomMapper;

  public CatalogApplicationService(CatalogCustomMapper catalogCustomMapper) {
    this.catalogCustomMapper = catalogCustomMapper;
  }

  /** カテゴリ一覧（[L2]で5件固定・非ページング）。 */
  public List<Category> listCategories() {
    return catalogCustomMapper.selectAllCategories().stream().map(this::toCategory).toList();
  }

  /** カテゴリ詳細。存在しなければ {@link ResourceNotFoundException}（→404）。 */
  public Category getCategory(String categoryId) {
    CategoryCustomEntity entity = catalogCustomMapper.selectCategoryById(categoryId);
    if (entity == null) {
      throw new ResourceNotFoundException("Category not found: " + categoryId);
    }
    return toCategory(entity);
  }

  /** カテゴリ内の商品一覧（ページング）。カテゴリ自体が存在しなければ {@link ResourceNotFoundException}（→404）。 */
  public Page<Product> listProductsByCategory(String categoryId, Integer page, Integer size) {
    if (catalogCustomMapper.selectCategoryById(categoryId) == null) {
      throw new ResourceNotFoundException("Category not found: " + categoryId);
    }
    PageRequest pageRequest = PageRequest.of(page, size);
    List<Product> content =
        catalogCustomMapper
            .selectProductsByCategoryId(categoryId, pageRequest.offset(), pageRequest.size())
            .stream()
            .map(this::toProduct)
            .toList();
    long totalElements = catalogCustomMapper.countProductsByCategoryId(categoryId);
    return Page.of(content, pageRequest.page(), pageRequest.size(), totalElements);
  }

  /** 商品詳細。存在しなければ {@link ResourceNotFoundException}（→404）。 */
  public Product getProduct(String productId) {
    ProductCustomEntity entity = catalogCustomMapper.selectProductById(productId);
    if (entity == null) {
      throw new ResourceNotFoundException("Product not found: " + productId);
    }
    return toProduct(entity);
  }

  /** 商品内の在庫アイテム一覧（ページング）。商品自体が存在しなければ {@link ResourceNotFoundException}（→404）。 */
  public Page<ItemSummary> listItemsByProduct(String productId, Integer page, Integer size) {
    if (catalogCustomMapper.selectProductById(productId) == null) {
      throw new ResourceNotFoundException("Product not found: " + productId);
    }
    PageRequest pageRequest = PageRequest.of(page, size);
    List<ItemSummary> content =
        catalogCustomMapper
            .selectItemsByProductId(productId, pageRequest.offset(), pageRequest.size())
            .stream()
            .map(this::toItemSummary)
            .toList();
    long totalElements = catalogCustomMapper.countItemsByProductId(productId);
    return Page.of(content, pageRequest.page(), pageRequest.size(), totalElements);
  }

  /** アイテム詳細（在庫status・qty非露出）。存在しなければ {@link ResourceNotFoundException}（→404）。 */
  public ItemDetail getItem(String itemId) {
    ItemDetailCustomEntity entity = catalogCustomMapper.selectItemById(itemId);
    if (entity == null) {
      throw new ResourceNotFoundException("Item not found: " + itemId);
    }
    return new ItemDetail(
        entity.getItemId(),
        entity.getProductId(),
        entity.getProductName(),
        entity.getProductDescription(),
        entity.getAttribute1(),
        entity.getListPrice(),
        StockStatusCalculator.of(entity.getQuantity()));
  }

  private Category toCategory(CategoryCustomEntity entity) {
    return new Category(entity.getCategoryId(), entity.getName(), entity.getDescription());
  }

  private Product toProduct(ProductCustomEntity entity) {
    return new Product(
        entity.getProductId(), entity.getCategoryId(), entity.getName(), entity.getDescription());
  }

  private ItemSummary toItemSummary(ItemSummaryCustomEntity entity) {
    return new ItemSummary(
        entity.getItemId(),
        entity.getProductId(),
        entity.getAttribute1(),
        entity.getListPrice(),
        StockStatusCalculator.of(entity.getQuantity()));
  }
}
