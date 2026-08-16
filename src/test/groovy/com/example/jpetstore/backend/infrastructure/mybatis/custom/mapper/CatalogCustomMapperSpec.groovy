package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper

import com.example.jpetstore.backend.support.IntegrationTestBase
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Tag

/**
 * #1: カタログ階層(category→product→item×inventory)のカスタムXMLマッパーを検証する。
 * [L2] 旧同値（categoryX→商品件数・productY→item一覧）・ページング(LIMIT/OFFSET)・COUNTを固定する。
 */
@Tag("integration")
class CatalogCustomMapperSpec extends IntegrationTestBase {

    @Autowired
    CatalogCustomMapper mapper

    def "[L2] selectAllCategoriesは5件をcategory_id昇順で返す"() {
        given:
        def rows = mapper.selectAllCategories()

        expect:
        rows.size() == 5
        rows*.categoryId == ["BIRDS", "CATS", "DOGS", "FISH", "REPTILES"]
    }

    def "selectCategoryByIdは存在すればエンティティを返し、存在しなければnullを返す"() {
        expect:
        mapper.selectCategoryById("DOGS").name == "Dogs"
        mapper.selectCategoryById("NOPE") == null
    }

    def "[L2] countProductsByCategoryIdはDOGSで6を返す(旧同値)"() {
        expect:
        mapper.countProductsByCategoryId("DOGS") == 6
        mapper.countProductsByCategoryId("FISH") == 4
    }

    def "selectProductsByCategoryIdはLIMIT/OFFSETでslice取得できる(size=2で3頁)"() {
        given:
        def page1 = mapper.selectProductsByCategoryId("DOGS", 0, 2)
        def page2 = mapper.selectProductsByCategoryId("DOGS", 2, 2)
        def page3 = mapper.selectProductsByCategoryId("DOGS", 4, 2)

        expect:
        page1.size() == 2
        page2.size() == 2
        page3.size() == 2
        (page1 + page2 + page3)*.productId.toSet().size() == 6
    }

    def "selectProductByIdは存在すればエンティティを返し、存在しなければnullを返す"() {
        expect:
        mapper.selectProductById("K9-RT-02").categoryId == "DOGS"
        mapper.selectProductById("NOPE") == null
    }

    def "[L2] countItemsByProductIdはK9-RT-02で最多の4を返す(旧同値)"() {
        expect:
        mapper.countItemsByProductId("K9-RT-02") == 4
    }

    def "selectItemsByProductIdはJOINでquantityを取得できる(在庫バッジ算出の元データ)"() {
        given:
        def rows = mapper.selectItemsByProductId("FI-SW-01", 0, 10)

        expect:
        rows.size() == 2
        def est2 = rows.find { it.itemId == "EST-2" }
        est2.quantity == 1
    }

    def "selectItemByIdはproduct名/descriptionをJOINで取得し、存在しなければnullを返す"() {
        given:
        def item = mapper.selectItemById("EST-22")

        expect:
        item.productId == "K9-RT-02"
        item.productName == "Labrador Retriever"
        item.quantity == 100

        and:
        mapper.selectItemById("NOPE") == null
    }
}
