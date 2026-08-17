package com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper;

import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.OrderHeaderWriteCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.OrderLineWriteCustomEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 注文（{@code t_order}/{@code t_order_line}）の永続化 Mapper（#8・{@code OrderApplicationService} から利用）。
 *
 * <p>JOINは伴わないが、生成キー（{@code useGeneratedKeys}）を用いる INSERT のため XML マッパー方式を採用する （{@code
 * backend-conventions} §9・カートと同じ配置規約）。定義は {@code resources/mapper/custom/OrderCustomMapper.xml}。
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
}
