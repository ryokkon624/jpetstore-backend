package com.example.jpetstore.backend.infrastructure.mybatis.cart

import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartItemCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ItemForCartCustomEntity
import spock.lang.Specification

/**
 * #29: Entity(Infrastructure層)→Domain変換の純粋関数を検証する（Repository実装内でのみ呼ばれる）。
 */
class CartConverterSpec extends Specification {

    private static CartItemCustomEntity cartItemEntity(
            String itemId, String productId, String productName, String attribute1,
            BigDecimal listPrice, int quantity, int stockQuantity) {
        def e = new CartItemCustomEntity()
        e.itemId = itemId
        e.productId = productId
        e.productName = productName
        e.attribute1 = attribute1
        e.listPrice = listPrice
        e.quantity = quantity
        e.stockQuantity = stockQuantity
        e
    }

    def "toCartはEntity一覧をCartItem一覧へ変換しsubtotalをサーバ計算する([L2])"() {
        given:
        def entities = [
                cartItemEntity("EST-1", "FI-SW-01", "Angelfish", "Large", 16.50, 2, 100),
                cartItemEntity("EST-22", "K9-RT-02", "Labrador Retriever", "Adult Male", 135.50, 1, 100),
        ]

        when:
        def cart = CartConverter.toCart(100L, entities)

        then:
        cart.cartId() == 100L
        cart.items().size() == 2
        // 16.50*2 + 135.50*1 = 168.50
        cart.subtotal() == 168.50
    }

    def "toCartは在庫減で数量が在庫を超えた行をexceedsStock=trueへ変換する(強制はしない・バッジ警告用)"() {
        given:
        def entities = [cartItemEntity("EST-2", "FI-SW-01", "Angelfish", "Small", 16.50, 5, 1)]

        when:
        def cart = CartConverter.toCart(100L, entities)

        then:
        cart.items()[0].quantity() == 5
        cart.items()[0].exceedsStock()
    }

    def "toStockAvailabilityはItemForCartCustomEntityをドメインVOへ変換する"() {
        given:
        def entity = new ItemForCartCustomEntity()
        entity.itemId = "EST-1"
        entity.stockQuantity = 100
        entity.currentQuantity = 2

        when:
        def stock = CartConverter.toStockAvailability(entity)

        then:
        stock.itemId() == "EST-1"
        stock.stockQuantity() == 100
        stock.currentQuantity() == 2
    }
}
