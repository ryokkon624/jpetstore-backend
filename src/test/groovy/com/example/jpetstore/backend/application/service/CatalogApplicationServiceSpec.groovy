package com.example.jpetstore.backend.application.service

import com.example.jpetstore.backend.domain.catalog.Category
import com.example.jpetstore.backend.domain.catalog.CatalogRepository
import com.example.jpetstore.backend.domain.catalog.ItemDetail
import com.example.jpetstore.backend.domain.catalog.ItemStock
import com.example.jpetstore.backend.domain.catalog.ItemSummary
import com.example.jpetstore.backend.domain.catalog.OrderabilityResult
import com.example.jpetstore.backend.domain.catalog.Product
import com.example.jpetstore.backend.domain.enums.StockStatus
import com.example.jpetstore.backend.domain.exception.ResourceNotFoundException
import spock.lang.Specification
import spock.lang.Unroll

/**
 * #30 C1: カタログ階層閲覧・検索・注文可否判定のユースケースを {@link CatalogRepository} mock（DB非依存）で検証する。
 *
 * <p>{@code CatalogCustomMapper} 直呼びから {@link CatalogRepository}（Domain層）経由へ retrofit した際の分岐（一覧の
 * 親not-foundガード・検索の空キーワード短絡・categoryId有無・checkOrderableの各分岐）を固定する。実際のSQL結果
 * （JOIN/LIMIT/OFFSET等）は {@code CatalogCustomMapperSpec}（Testcontainers・XML/Mapper自体は無変更）が引き続き検証する。
 */
class CatalogApplicationServiceSpec extends Specification {

    CatalogRepository catalogRepository = Mock()
    CatalogApplicationService service = new CatalogApplicationService(catalogRepository)

    def "listCategories: repositoryの一覧をそのまま返す"() {
        given:
        def categories = [new Category("CATS", "Cats", "desc")]

        when:
        def result = service.listCategories()

        then:
        1 * catalogRepository.listCategories() >> categories
        result == categories
    }

    def "getCategory: 存在すればCategoryを返す"() {
        given:
        def category = new Category("CATS", "Cats", "desc")

        when:
        def result = service.getCategory("CATS")

        then:
        1 * catalogRepository.findCategoryById("CATS") >> Optional.of(category)
        result == category
    }

    def "getCategory: 存在しなければResourceNotFoundException"() {
        given:
        catalogRepository.findCategoryById("NOPE") >> Optional.empty()

        when:
        service.getCategory("NOPE")

        then:
        thrown(ResourceNotFoundException)
    }

    def "listProductsByCategory: カテゴリが存在すれば一覧+件数を取得する"() {
        given:
        def category = new Category("CATS", "Cats", "desc")
        def products = [new Product("FI-SW-01", "CATS", "name", "desc")]

        when:
        def page = service.listProductsByCategory("CATS", 1, 12)

        then:
        1 * catalogRepository.findCategoryById("CATS") >> Optional.of(category)
        1 * catalogRepository.listProductsByCategoryId("CATS", 0, 12) >> products
        1 * catalogRepository.countProductsByCategoryId("CATS") >> 1L
        page.content() == products
        page.totalElements() == 1L
    }

    def "listProductsByCategory: カテゴリが存在しなければResourceNotFoundExceptionで一覧/件数クエリは呼ばない"() {
        given:
        catalogRepository.findCategoryById("NOPE") >> Optional.empty()

        when:
        service.listProductsByCategory("NOPE", 1, 12)

        then:
        thrown(ResourceNotFoundException)
        0 * catalogRepository.listProductsByCategoryId(_, _, _)
        0 * catalogRepository.countProductsByCategoryId(_)
    }

    def "getProduct: 存在すればProductを返す"() {
        given:
        def product = new Product("FI-SW-01", "CATS", "name", "desc")

        when:
        def result = service.getProduct("FI-SW-01")

        then:
        1 * catalogRepository.findProductById("FI-SW-01") >> Optional.of(product)
        result == product
    }

    def "getProduct: 存在しなければResourceNotFoundException"() {
        given:
        catalogRepository.findProductById("NOPE") >> Optional.empty()

        when:
        service.getProduct("NOPE")

        then:
        thrown(ResourceNotFoundException)
    }

    def "listItemsByProduct: 商品が存在すれば一覧+件数を取得する"() {
        given:
        def product = new Product("FI-SW-01", "CATS", "name", "desc")
        def items = [new ItemSummary("EST-1", "FI-SW-01", "attr", 10.0G, StockStatus.IN_STOCK)]

        when:
        def page = service.listItemsByProduct("FI-SW-01", 1, 12)

        then:
        1 * catalogRepository.findProductById("FI-SW-01") >> Optional.of(product)
        1 * catalogRepository.listItemsByProductId("FI-SW-01", 0, 12) >> items
        1 * catalogRepository.countItemsByProductId("FI-SW-01") >> 1L
        page.content() == items
    }

    def "listItemsByProduct: 商品が存在しなければResourceNotFoundExceptionで一覧/件数クエリは呼ばない"() {
        given:
        catalogRepository.findProductById("NOPE") >> Optional.empty()

        when:
        service.listItemsByProduct("NOPE", 1, 12)

        then:
        thrown(ResourceNotFoundException)
        0 * catalogRepository.listItemsByProductId(_, _, _)
        0 * catalogRepository.countItemsByProductId(_)
    }

    def "searchProducts: 空キーワードは空Pageへ短絡しrepositoryへ一切問い合わせない"() {
        when:
        def page = service.searchProducts(keyword, null, 1, 12)

        then:
        0 * catalogRepository.searchProducts(_, _, _, _)
        0 * catalogRepository.countSearchProducts(_, _)
        page.content().isEmpty()
        page.totalElements() == 0L

        where:
        keyword << [null, "", "   "]
    }

    def "searchProducts: categoryIdが空白のみならnormalizedはnullでrepositoryへ渡す"() {
        given:
        def products = [new Product("FI-SW-01", "CATS", "name", "desc")]

        when:
        def page = service.searchProducts("dog", "  ", 1, 12)

        then:
        1 * catalogRepository.searchProducts(["%dog%"], null, 0, 12) >> products
        1 * catalogRepository.countSearchProducts(["%dog%"], null) >> 1L
        page.content() == products
    }

    def "searchProducts: categoryIdが指定されていればそのままrepositoryへ渡す"() {
        when:
        service.searchProducts("dog", "CATS", 1, 12)

        then:
        // given:の裸stubとthen:の引数一致インタラクションを分けるとthen:が優先されgiven:の>>が無視される
        // (Spockの既知挙動・backend-conventions §9)。1つのthen:にmatcherと>>をまとめる。
        1 * catalogRepository.searchProducts(["%dog%"], "CATS", 0, 12) >> []
        1 * catalogRepository.countSearchProducts(["%dog%"], "CATS") >> 0L
    }

    def "getItem: 存在すればItemDetailを返す"() {
        given:
        def detail = new ItemDetail("EST-1", "FI-SW-01", "name", "desc", "attr", 10.0G, StockStatus.IN_STOCK)

        when:
        def result = service.getItem("EST-1")

        then:
        1 * catalogRepository.findItemDetailById("EST-1") >> Optional.of(detail)
        result == detail
    }

    def "getItem: 存在しなければResourceNotFoundException"() {
        given:
        catalogRepository.findItemDetailById("NOPE") >> Optional.empty()

        when:
        service.getItem("NOPE")

        then:
        thrown(ResourceNotFoundException)
    }

    def "checkOrderable: itemが存在しなければResourceNotFoundException"() {
        given:
        catalogRepository.findItemStockById("NOPE") >> Optional.empty()

        when:
        service.checkOrderable("NOPE", 1)

        then:
        thrown(ResourceNotFoundException)
    }

    @Unroll
    def "checkOrderable: quantity=#quantity, stock=#stockQuantity => orderable=#expectedOrderable, reason=#expectedReason"() {
        given:
        catalogRepository.findItemStockById("EST-1") >> Optional.of(new ItemStock("EST-1", stockQuantity))

        when:
        def result = service.checkOrderable("EST-1", quantity)

        then:
        result.orderable() == expectedOrderable
        result.reason() == expectedReason

        where:
        quantity | stockQuantity || expectedOrderable | expectedReason
        0        | 10            || false             | OrderabilityResult.REASON_INVALID_QUANTITY
        -1       | 10            || false             | OrderabilityResult.REASON_INVALID_QUANTITY
        1        | 0             || false             | OrderabilityResult.REASON_OUT_OF_STOCK
        11       | 10            || false             | OrderabilityResult.REASON_EXCEEDS_STOCK
        5        | 10            || true               | null
    }
}
