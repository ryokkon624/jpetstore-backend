package com.example.jpetstore.backend.infrastructure.mybatis.cart

import com.example.jpetstore.backend.domain.cart.CartItem
import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.domain.security.CurrentUserProvider
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartHeaderCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartItemWriteCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.CartCustomMapper
import spock.lang.Specification

/**
 * #29 perf是正: {@link MyBatisCartRepository} の各メソッドが {@link CartCustomMapper} を何回・どの組み合わせで
 * 呼ぶかを純UT（Mapper mock・DB非依存）で検証する。CartApplicationServiceSpec（Repository mock）と本specを
 * 組み合わせることで「書込1操作あたりのSQL発行数」を実DBに依存せず裏取りできる。
 */
class MyBatisCartRepositorySpec extends Specification {

    private static final Long USER_ID = 1L
    private static final Long CART_ID = 100L

    CartCustomMapper cartCustomMapper = Mock()
    CurrentUserProvider currentUserProvider = Stub() {
        requireCurrentUser() >> new AuthenticatedUser(USER_ID, "cart_user", ["USER"])
    }

    MyBatisCartRepository repository = new MyBatisCartRepository(cartCustomMapper, currentUserProvider)

    def "ensureCartはcartCustomMapper#ensureCartのみを呼び、selectCartItemsは呼ばない(items非ロード)"() {
        when:
        def cartId = repository.ensureCart(USER_ID)

        then:
        // given:とthen:で同一呼び出しに対しstub(>>)とcount検証(N *)を分けて書くと、then:側が優先されgiven:側の
        // 副作用クロージャが無視される(Spockの既知挙動)。1つのthen:インタラクションにmatcherと>>をまとめて書く。
        1 * cartCustomMapper.ensureCart({ CartHeaderCustomEntity h -> h.userId == USER_ID }) >> { CartHeaderCustomEntity h ->
            h.cartId = CART_ID
        }
        0 * cartCustomMapper.selectCartItems(_)
        cartId == CART_ID
    }

    def "findByCartIdはcartCustomMapper#selectCartItemsのみを呼び、ensureCartは呼ばない(select-only)"() {
        when:
        def cart = repository.findByCartId(CART_ID)

        then:
        1 * cartCustomMapper.selectCartItems(CART_ID) >> []
        0 * cartCustomMapper.ensureCart(_)
        cart.cartId() == CART_ID
    }

    def "findByUserIdはensureCart+selectCartItemsを1回ずつ呼ぶ(合成。冗長な二重呼びをしない)"() {
        when:
        def cart = repository.findByUserId(USER_ID)

        then:
        1 * cartCustomMapper.ensureCart(_) >> { CartHeaderCustomEntity h -> h.cartId = CART_ID }
        1 * cartCustomMapper.selectCartItems(CART_ID) >> []
        cart.cartId() == CART_ID
    }

    def "upsertItemはcartCustomMapper#upsertCartItemQuantityを1回だけ呼び、現在ユーザをWHOへ補完する"() {
        given:
        def line = CartItem.reconstruct("EST-1", null, null, null, 5, null, 100)

        when:
        repository.upsertItem(CART_ID, line)

        then:
        1 * cartCustomMapper.upsertCartItemQuantity({ CartItemWriteCustomEntity w ->
            w.cartId == CART_ID && w.itemId == "EST-1" && w.quantity == 5 &&
                    w.createUserId == USER_ID && w.updateUserId == USER_ID
        })
    }

    def "removeItemはcartCustomMapper#deleteCartItemを1回だけ呼ぶ"() {
        when:
        repository.removeItem(CART_ID, "EST-1")

        then:
        1 * cartCustomMapper.deleteCartItem(CART_ID, "EST-1")
    }
}
