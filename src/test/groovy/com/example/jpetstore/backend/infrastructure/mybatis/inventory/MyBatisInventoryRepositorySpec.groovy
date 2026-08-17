package com.example.jpetstore.backend.infrastructure.mybatis.inventory

import com.example.jpetstore.backend.domain.security.AuthenticatedUser
import com.example.jpetstore.backend.domain.security.CurrentUserProvider
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.InventoryDecreaseCustomEntity
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.InventoryCustomMapper
import spock.lang.Specification

/**
 * #30: {@link MyBatisInventoryRepository} が {@link InventoryCustomMapper} を1回だけ呼び、affected rowsを
 * そのまま返す・WHOカラム（update_user_id）を{@link CurrentUserProvider}から解決することを純UT（Mapper mock・
 * DB非依存）で検証する。
 */
class MyBatisInventoryRepositorySpec extends Specification {

    private static final Long USER_ID = 7L

    InventoryCustomMapper inventoryCustomMapper = Mock()
    CurrentUserProvider currentUserProvider = Stub() {
        requireCurrentUser() >> new AuthenticatedUser(USER_ID, "order_user", ["USER"])
    }

    MyBatisInventoryRepository repository = new MyBatisInventoryRepository(inventoryCustomMapper, currentUserProvider)

    def "decrease: inventoryCustomMapper#decreaseInventoryを1回呼びaffected rowsをそのまま返す"() {
        when:
        def rows = repository.decrease("EST-1", 5)

        then:
        1 * inventoryCustomMapper.decreaseInventory({ InventoryDecreaseCustomEntity e ->
            e.itemId == "EST-1" && e.quantity == 5 && e.updateUserId == USER_ID
        }) >> 1
        rows == 1
    }

    def "decrease: affected rows=0(在庫不足)はそのまま0を返す(例外化はApplication層の責務)"() {
        when:
        def rows = repository.decrease("EST-1", 5)

        then:
        1 * inventoryCustomMapper.decreaseInventory(_) >> 0
        rows == 0
    }
}
