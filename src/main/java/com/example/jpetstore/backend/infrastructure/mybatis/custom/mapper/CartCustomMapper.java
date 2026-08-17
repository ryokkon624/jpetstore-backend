package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper;

import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartHeaderCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartItemCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.CartItemWriteCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.ItemForCartCustomEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * カート（{@code t_cart}/{@code t_cart_item}）の永続化 Mapper（#4・{@code CartApplicationService} から利用）。
 *
 * <p>JOIN（item×inventory=在庫、item×product=商品名）・upsert（{@code ON DUPLICATE KEY
 * UPDATE}）を伴うため、XMLマッパー方式を採用する（{@code backend-conventions} §9）。定義は {@code
 * resources/mapper/custom/CartCustomMapper.xml}。
 *
 * <p>MyBatis Generator の生成対象外（Generator にはカートテーブルを登録しない。カタログと同じ方針）。
 */
@Mapper
public interface CartCustomMapper {

  /**
   * 呼び出し元ユーザの {@code t_cart} を取得し、無ければ作成する（upsert-get-id）。{@code cart.cartId} に生成済み/既存のカートIDが補完される。
   */
  void ensureCart(CartHeaderCustomEntity cart);

  /** カート内アイテム一覧（表示・小計サーバ計算用）。JOINでproductName/attribute1/listPrice/在庫qtyを取得する。 */
  List<CartItemCustomEntity> selectCartItems(@Param("cartId") Long cartId);

  /**
   * 追加/更新の事前チェック用に、アイテムの在庫qty・当該カート内での既存数量を取得する。{@code m_item} に該当行が無ければ {@code
   * null}（アイテム不存在＝呼び出し元で404）。
   */
  ItemForCartCustomEntity selectItemForCart(
      @Param("itemId") String itemId, @Param("cartId") Long cartId);

  /**
   * カート明細を最終的な絶対数量でupsertする（{@code ON DUPLICATE KEY UPDATE}）。加算/クランプ等の算出は呼び出し元（{@code
   * CartApplicationService}）が済ませ、ここでは単文アトミックな書き込みのみ行う（D3）。
   */
  void upsertCartItemQuantity(CartItemWriteCustomEntity item);

  /** カート明細を削除する（数量0以下・明示削除の両方から呼ばれる。単一表+単一削除経路＝AC2・幽霊行=ID-17を踏襲しない）。 */
  void deleteCartItem(@Param("cartId") Long cartId, @Param("itemId") String itemId);

  /** カート明細を全削除する（#8・注文確定成功後のカート全クリア）。 */
  void deleteCartItems(@Param("cartId") Long cartId);
}
