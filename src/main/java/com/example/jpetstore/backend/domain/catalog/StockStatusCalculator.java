package com.example.jpetstore.backend.domain.catalog;

import com.example.jpetstore.backend.domain.enums.StockStatus;

/**
 * #1 AC3・論点2（ユーザー承認 2026-08-16）: 在庫数(qty)からバッジ用の {@link StockStatus} を算出する。
 *
 * <p>在庫ステータス自体はm_code区分値として登録し{@code StockStatus}を生成しているが、閾値算出ロジック（{@code
 * of(qty)}）は生成enumに持たせない（{@code ./gradlew generateEnums} 再実行のたびに上書きされ消えるため）。このクラスは非生成であり手動編集してよい。
 *
 * <p>qty は在庫数そのものであり、呼び出し側（controller/DTO）はこの変換結果（{@link StockStatus}）のみを外部応答に含めること。qty
 * 自体をレスポンスへ含めない（AC3・在庫数非露出）。
 */
public final class StockStatusCalculator {

  /** 残少と判定する閾値（ユーザー承認 2026-08-16・N=5）。0 < qty <= N は残少。 */
  public static final int LOW_STOCK_THRESHOLD = 5;

  private StockStatusCalculator() {}

  public static StockStatus of(int quantity) {
    if (quantity <= 0) {
      return StockStatus.OUT_OF_STOCK;
    }
    if (quantity <= LOW_STOCK_THRESHOLD) {
      return StockStatus.LOW_STOCK;
    }
    return StockStatus.IN_STOCK;
  }
}
