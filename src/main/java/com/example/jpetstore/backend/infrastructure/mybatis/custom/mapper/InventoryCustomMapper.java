package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper;

import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.InventoryDecreaseCustomEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 在庫（{@code t_inventory}）のガード付きアトミック減算 Mapper（#8 AC2・architecture-conventions §4.1）。
 *
 * <p>定義は {@code resources/mapper/custom/InventoryCustomMapper.xml}。{@code t_inventory} は MyBatis
 * Generator の生成対象外（在庫はこのガード付き減算のみが唯一の更新経路であるべきため、生成物由来の任意更新 メソッドを持たせない）。
 */
@Mapper
public interface InventoryCustomMapper {

  /**
   * {@code quantity} 分の在庫をガード付きで減算する（{@code UPDATE ... WHERE item_id = :id AND quantity >=
   * :quantity}）。affected rows（返り値）が 0 の場合は在庫不足（在庫が足りない、または同時発注に競合負けした） ことを意味する。呼び出し元が {@code
   * AffectedRows.requireUpdated(rows, supplier)} で {@code InsufficientStockException} に正規化する。
   */
  int decreaseInventory(InventoryDecreaseCustomEntity entity);
}
