package com.example.jpetstore.backend.infrastructure.mybatis.catalog

import com.example.jpetstore.backend.domain.enums.StockStatus
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CategoryCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ItemDetailCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ItemSummaryCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ProductCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.CatalogCustomMapper
import spock.lang.Specification

/**
 * #30: {@link MyBatisCatalogRepository} が {@link CatalogCustomMapper} を各メソッド1回だけ呼び、Entity→Domain変換
 * （{@code StockStatus} 射影・raw {@link com.example.jpetstore.backend.domain.catalog.ItemStock} 抽出を含む）を
 * 正しく行うことを純UT（Mapper mock・DB非依存）で検証する。
 */
class MyBatisCatalogRepositorySpec extends Specification {

    CatalogCustomMapper catalogCustomMapper = Mock()
    MyBatisCatalogRepository repository = new MyBatisCatalogRepository(catalogCustomMapper)

    private static CategoryCustomEntity categoryEntity() {
        def e = new CategoryCustomEntity()
        e.categoryId = "CATS"
        e.name = "Cats"
        e.description = "desc"
        e
    }

    private static ProductCustomEntity productEntity() {
        def e = new ProductCustomEntity()
        e.productId = "FI-SW-01"
        e.categoryId = "CATS"
        e.name = "name"
        e.description = "desc"
        e
    }

    private static ItemSummaryCustomEntity itemSummaryEntity(int quantity) {
        def e = new ItemSummaryCustomEntity()
        e.itemId = "EST-1"
        e.productId = "FI-SW-01"
        e.attribute1 = "attr"
        e.listPrice = 10.0G
        e.quantity = quantity
        e
    }

    private static ItemDetailCustomEntity itemDetailEntity(int quantity) {
        def e = new ItemDetailCustomEntity()
        e.itemId = "EST-1"
        e.productId = "FI-SW-01"
        e.productName = "name"
        e.productDescription = "desc"
        e.attribute1 = "attr"
        e.listPrice = 10.0G
        e.quantity = quantity
        e
    }

    def "listCategories: selectAllCategoriesを1回呼びCategoryへ変換する"() {
        when:
        def result = repository.listCategories()

        then:
        1 * catalogCustomMapper.selectAllCategories() >> [categoryEntity()]
        result == [new com.example.jpetstore.backend.domain.catalog.Category("CATS", "Cats", "desc")]
    }

    def "findCategoryById: nullならOptional.emptyを返す"() {
        when:
        def result = repository.findCategoryById("NOPE")

        then:
        1 * catalogCustomMapper.selectCategoryById("NOPE") >> null
        result.isEmpty()
    }

    def "findCategoryById: 存在すればOptionalでCategoryを返す"() {
        when:
        def result = repository.findCategoryById("CATS")

        then:
        1 * catalogCustomMapper.selectCategoryById("CATS") >> categoryEntity()
        result.get().categoryId() == "CATS"
    }

    def "findProductById: 存在すればOptionalでProductを返す"() {
        when:
        def result = repository.findProductById("FI-SW-01")

        then:
        1 * catalogCustomMapper.selectProductById("FI-SW-01") >> productEntity()
        result.get().productId() == "FI-SW-01"
    }

    def "listItemsByProductId: selectItemsByProductIdを1回呼びStockStatus射影する"() {
        when:
        def result = repository.listItemsByProductId("FI-SW-01", 0, 12)

        then:
        1 * catalogCustomMapper.selectItemsByProductId("FI-SW-01", 0, 12) >> [itemSummaryEntity(0)]
        result[0].stockStatus() == StockStatus.OUT_OF_STOCK
    }

    def "findItemDetailById: selectItemByIdを1回呼びStockStatus射影する(qtyはItemDetailに含まれない)"() {
        when:
        def result = repository.findItemDetailById("EST-1")

        then:
        1 * catalogCustomMapper.selectItemById("EST-1") >> itemDetailEntity(50)
        result.get().stockStatus() == StockStatus.IN_STOCK
    }

    def "findItemStockById: selectItemByIdを1回呼びraw qtyを保持するItemStockを返す"() {
        when:
        def result = repository.findItemStockById("EST-1")

        then:
        1 * catalogCustomMapper.selectItemById("EST-1") >> itemDetailEntity(7)
        result.get().itemId() == "EST-1"
        result.get().stockQuantity() == 7
    }

    def "findItemStockById: nullならOptional.emptyを返す"() {
        when:
        def result = repository.findItemStockById("NOPE")

        then:
        1 * catalogCustomMapper.selectItemById("NOPE") >> null
        result.isEmpty()
    }
}
