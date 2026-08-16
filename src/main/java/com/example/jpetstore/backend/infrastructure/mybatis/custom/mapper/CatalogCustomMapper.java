package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper;

import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CategoryCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ItemDetailCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ItemSummaryCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ProductCustomEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * カタログ階層（category → product → item ＋ inventory）の参照専用 Mapper（#1・{@code CatalogApplicationService}
 * から利用）。
 *
 * <p>読み取り専用＋階層＋ページング(LIMIT/OFFSET)＋JOIN（item×inventory=在庫、item×product=商品名）＋COUNT
 * を伴うため、XMLマッパー方式を採用する（{@code backend-conventions} §9）。定義は {@code
 * resources/mapper/custom/CatalogCustomMapper.xml}。
 *
 * <p>MyBatis Generator の生成対象外（Generator には catalog テーブルを登録しない。ユーザー承認 2026-08-16）。
 */
@Mapper
public interface CatalogCustomMapper {

  List<CategoryCustomEntity> selectAllCategories();

  CategoryCustomEntity selectCategoryById(@Param("categoryId") String categoryId);

  List<ProductCustomEntity> selectProductsByCategoryId(
      @Param("categoryId") String categoryId,
      @Param("offset") int offset,
      @Param("limit") int limit);

  long countProductsByCategoryId(@Param("categoryId") String categoryId);

  ProductCustomEntity selectProductById(@Param("productId") String productId);

  List<ItemSummaryCustomEntity> selectItemsByProductId(
      @Param("productId") String productId, @Param("offset") int offset, @Param("limit") int limit);

  long countItemsByProductId(@Param("productId") String productId);

  ItemDetailCustomEntity selectItemById(@Param("itemId") String itemId);

  /**
   * 商品検索（#2 AC1/AC3・ID-29）。{@code patterns} は {@code ProductSearchTerms} が語分割・LIKEエスケープ済みの {@code
   * %...%} パターン列（空であってはならない。空キーワードは {@code CatalogApplicationService}
   * 側で空結果へ短絡する）。name/category_id/description のいずれかに各パターンがOR一致し、パターン間もOR結合する （legacy {@code
   * searchProductList} の keyword OR 挙動を踏襲・[L2]）。{@code categoryId} は任意で、指定時は AND 条件でその配下に限定する。
   */
  List<ProductCustomEntity> searchProducts(
      @Param("patterns") List<String> patterns,
      @Param("categoryId") String categoryId,
      @Param("offset") int offset,
      @Param("limit") int limit);

  long countSearchProducts(
      @Param("patterns") List<String> patterns, @Param("categoryId") String categoryId);
}
