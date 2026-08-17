package com.example.jpetstore.backend.infrastructure.mybatis.inventory;

import com.example.jpetstore.backend.domain.inventory.InventoryRepository;
import com.example.jpetstore.backend.domain.security.CurrentUserProvider;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.InventoryDecreaseCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.InventoryCustomMapper;
import org.springframework.stereotype.Repository;

/**
 * {@link InventoryRepository} の MyBatis実装（#30・{@code backend-conventions} §9・#29 Cart PoCテンプレ踏襲）。
 *
 * <p>{@link InventoryCustomMapper} を保持し、affected rows のみを返す（0=在庫不足の意味づけ・{@code
 * InsufficientStockException} への変換はApplication層＝orchestrationの関心事）。{@code update_user_id}（WHOカラム）は
 * {@link CurrentUserProvider} から明示的に解決する（{@code MyBatisCartRepository}/{@code
 * MyBatisOrderRepository} と同じ思想）。
 */
@Repository
public class MyBatisInventoryRepository implements InventoryRepository {

  private final InventoryCustomMapper inventoryCustomMapper;
  private final CurrentUserProvider currentUserProvider;

  public MyBatisInventoryRepository(
      InventoryCustomMapper inventoryCustomMapper, CurrentUserProvider currentUserProvider) {
    this.inventoryCustomMapper = inventoryCustomMapper;
    this.currentUserProvider = currentUserProvider;
  }

  @Override
  public int decrease(String itemId, int quantity) {
    InventoryDecreaseCustomEntity entity = new InventoryDecreaseCustomEntity();
    entity.setItemId(itemId);
    entity.setQuantity(quantity);
    entity.setUpdateUserId(currentUserProvider.requireCurrentUser().userId());
    return inventoryCustomMapper.decreaseInventory(entity);
  }
}
