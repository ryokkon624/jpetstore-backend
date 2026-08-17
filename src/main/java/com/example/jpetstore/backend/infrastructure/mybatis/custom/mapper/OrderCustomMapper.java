package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper;

import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.OrderHeaderCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.OrderHeaderWriteCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.OrderLineViewCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.OrderLineWriteCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.OrderSummaryCustomEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 注文（{@code t_order}/{@code t_order_line}）の永続化 Mapper（#8/#9/#10・{@code OrderApplicationService}
 * から利用）。
 *
 * <p>書込（INSERT）はJOINを伴わないが生成キー（{@code useGeneratedKeys}）を用いるため、読取（#9/#10）はJOIN・LIMIT/OFFSET・COUNT
 * を伴うため、いずれもXMLマッパー方式を採用する（{@code backend-conventions} §9・カタログと同じ配置規約）。定義は {@code
 * resources/mapper/custom/OrderCustomMapper.xml}。
 *
 * <p>{@code t_order}/{@code t_order_line} は純追記表（architecture-conventions §4.3・並行制御は在庫ガード減算が 主機構）のため
 * MyBatis Generator の生成対象外とし、意図しない update/delete 系メソッドを持たせない。
 */
@Mapper
public interface OrderCustomMapper {

  /** 注文ヘッダを1件INSERTする。{@code header.orderId} に生成された AUTO_INCREMENT キー（ID-23）が補完される。 */
  void insertOrderHeader(OrderHeaderWriteCustomEntity header);

  /** 注文明細を1件INSERTする。 */
  void insertOrderLine(OrderLineWriteCustomEntity line);

  /** #9 AC1: 本人の注文履歴一覧を新しい順（{@code order_id DESC}）でページング取得する。 */
  List<OrderSummaryCustomEntity> selectOrdersByUserId(
      @Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);

  /** #9 AC1: 本人の注文件数（ページング用total）。 */
  long countOrdersByUserId(@Param("userId") Long userId);

  /** #10: 注文ヘッダを1件取得する（所有者解決＋応答の両方に使う・存在しなければ{@code null}）。 */
  OrderHeaderCustomEntity selectOrderHeaderById(@Param("orderId") Long orderId);

  /** #10 AC2: 注文明細をline_num順で取得する（{@code t_order_line ⋈ m_item ⋈ m_product}で商品名を補完）。 */
  List<OrderLineViewCustomEntity> selectOrderLinesByOrderId(@Param("orderId") Long orderId);
}
