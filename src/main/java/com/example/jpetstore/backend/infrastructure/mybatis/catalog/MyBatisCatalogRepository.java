package com.example.jpetstore.backend.infrastructure.mybatis.catalog;

import com.example.jpetstore.backend.domain.catalog.CatalogRepository;
import com.example.jpetstore.backend.domain.catalog.Category;
import com.example.jpetstore.backend.domain.catalog.ItemDetail;
import com.example.jpetstore.backend.domain.catalog.ItemStock;
import com.example.jpetstore.backend.domain.catalog.ItemSummary;
import com.example.jpetstore.backend.domain.catalog.Product;
import com.example.jpetstore.backend.domain.catalog.StockStatusCalculator;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CategoryCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ItemDetailCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ItemSummaryCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ProductCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.CatalogCustomMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link CatalogRepository} の MyBatis実装（#30・{@code backend-conventions} §9・#29 Cart PoCテンプレ踏襲）。
 *
 * <p>{@link CatalogCustomMapper} を保持し、戻り値は常にDomainモデルのみ（{@code *CustomEntity} をApplication層へ出さない）。
 * {@code StockStatus} 射影（{@link StockStatusCalculator#of}）を含むEntity→record変換はここに閉じる。
 */
@Repository
public class MyBatisCatalogRepository implements CatalogRepository {

  private final CatalogCustomMapper catalogCustomMapper;

  public MyBatisCatalogRepository(CatalogCustomMapper catalogCustomMapper) {
    this.catalogCustomMapper = catalogCustomMapper;
  }

  @Override
  public List<Category> listCategories() {
    return catalogCustomMapper.selectAllCategories().stream().map(this::toCategory).toList();
  }

  @Override
  public Optional<Category> findCategoryById(String categoryId) {
    return Optional.ofNullable(catalogCustomMapper.selectCategoryById(categoryId))
        .map(this::toCategory);
  }

  @Override
  public List<Product> listProductsByCategoryId(String categoryId, int offset, int limit) {
    return catalogCustomMapper.selectProductsByCategoryId(categoryId, offset, limit).stream()
        .map(this::toProduct)
        .toList();
  }

  @Override
  public long countProductsByCategoryId(String categoryId) {
    return catalogCustomMapper.countProductsByCategoryId(categoryId);
  }

  @Override
  public Optional<Product> findProductById(String productId) {
    return Optional.ofNullable(catalogCustomMapper.selectProductById(productId))
        .map(this::toProduct);
  }

  @Override
  public List<ItemSummary> listItemsByProductId(String productId, int offset, int limit) {
    return catalogCustomMapper.selectItemsByProductId(productId, offset, limit).stream()
        .map(this::toItemSummary)
        .toList();
  }

  @Override
  public long countItemsByProductId(String productId) {
    return catalogCustomMapper.countItemsByProductId(productId);
  }

  @Override
  public List<Product> searchProducts(
      List<String> patterns, String categoryId, int offset, int limit) {
    return catalogCustomMapper.searchProducts(patterns, categoryId, offset, limit).stream()
        .map(this::toProduct)
        .toList();
  }

  @Override
  public long countSearchProducts(List<String> patterns, String categoryId) {
    return catalogCustomMapper.countSearchProducts(patterns, categoryId);
  }

  @Override
  public Optional<ItemDetail> findItemDetailById(String itemId) {
    return Optional.ofNullable(catalogCustomMapper.selectItemById(itemId)).map(this::toItemDetail);
  }

  @Override
  public Optional<ItemStock> findItemStockById(String itemId) {
    return Optional.ofNullable(catalogCustomMapper.selectItemById(itemId))
        .map(entity -> new ItemStock(entity.getItemId(), entity.getQuantity()));
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

  private ItemDetail toItemDetail(ItemDetailCustomEntity entity) {
    return new ItemDetail(
        entity.getItemId(),
        entity.getProductId(),
        entity.getProductName(),
        entity.getProductDescription(),
        entity.getAttribute1(),
        entity.getListPrice(),
        StockStatusCalculator.of(entity.getQuantity()));
  }
}
