package com.example.jpetstore.backend.domain.inventory;

/**
 * 在庫（{@code t_inventory}）のガード付きアトミック減算の永続化アクセスの唯一の入口（#30・{@code backend-conventions}
 * §1/§2/§9・architecture-conventions §4.1）。
 *
 * <p>実装は Infrastructure 層（{@code
 * infrastructure.mybatis.inventory.MyBatisInventoryRepository}）に置く（依存性逆転）。 Application 層（{@code
 * OrderApplicationService}）はこのインターフェイスのみに依存し、MyBatis の {@code InventoryCustomMapper} や {@code
 * *CustomEntity} を直接扱わない。
 */
public interface InventoryRepository {

  /**
   * {@code quantity} 分の在庫をガード付きで減算する（{@code UPDATE ... WHERE item_id = :id AND quantity >=
   * :quantity}）。
   *
   * @return affected rows。0 の場合は在庫不足（在庫が足りない、または同時発注に競合負けした）ことを意味し、呼び出し元が {@code
   *     AffectedRows.requireUpdated(rows, supplier)} で {@code InsufficientStockException} に正規化する （0
   *     rows=在庫不足→throw の固定順/all-or-nothingセマンティクスはorchestrationの知識のためApplication側で呼ぶ）。
   */
  int decrease(String itemId, int quantity);
}
