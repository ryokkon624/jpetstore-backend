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

    def "[L2] searchProductsは単一語'%dog%'でcategory_id/descriptionのOR一致7件を返す(旧同値)"() {
        given:
        // category_id=DOGSの6件 + REPTILESのRP-SN-01(description='Doubles as a watch dog')が
        // descriptionマッチでヒットする（legacyのkeyword検索がcategory横断でヒットする挙動を踏襲）。
        def rows = mapper.searchProducts(["%dog%"], null, 0, 20)
        def count = mapper.countSearchProducts(["%dog%"], null)

        expect:
        count == 7
        rows.size() == 7
        rows*.productId.toSet() == ["K9-BD-01", "K9-PO-02", "K9-DL-01", "K9-RT-01", "K9-RT-02", "K9-CW-01", "RP-SN-01"].toSet()
    }

    def "[L2] searchProductsは複数語をOR結合する('%fish%'と'%dog%'で11件=4+7、旧同値)"() {
        expect:
        mapper.countSearchProducts(["%fish%", "%dog%"], null) == 11
    }

    def "AC1: searchProductsはcategoryId指定でその配下に限定する(dog×DOGS=6件、RP-SN-01を除外)"() {
        given:
        def rows = mapper.searchProducts(["%dog%"], "DOGS", 0, 20)

        expect:
        mapper.countSearchProducts(["%dog%"], "DOGS") == 6
        rows.size() == 6
        rows.every { it.categoryId == "DOGS" }
    }

    def "ID-29: LIKEメタ文字はエスケープ済みならリテラル一致し、seedにアンダースコアを含む語が無いため0件になる"() {
        expect:
        mapper.countSearchProducts(["%\\_%"], null) == 0
    }

    def "AC-neg1: SQLメタ文字を含む語(パラメタライズ)でも注入は成立せず単なる不一致で0件になる"() {
        expect:
        mapper.countSearchProducts(["%' OR 1=1 --%"], null) == 0
    }

    def "searchProductsはLIMIT/OFFSETでページングできる(dog=7件をsize=3で3頁)"() {
        given:
        def page1 = mapper.searchProducts(["%dog%"], null, 0, 3)
        def page2 = mapper.searchProducts(["%dog%"], null, 3, 3)
        def page3 = mapper.searchProducts(["%dog%"], null, 6, 3)

        expect:
        page1.size() == 3
        page2.size() == 3
        page3.size() == 1
        (page1 + page2 + page3)*.productId.toSet().size() == 7
    }
}
