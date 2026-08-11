package com.example.jpetstore.backend.infrastructure.audit;

/**
 * WHOカラム（create_program / update_program）に記録する「機能識別子（ClassName#method）」を、 リクエストを処理するスレッド内で保持する ThreadLocal
 * ホルダー。
 *
 * <p>set-once 方式: 最初に設定した「最外の業務サービス」が owner となり、以降のネストした呼び出しでは 上書きされない。owner だけが最後に {@link
 * #clear()} を行う。値の設定・破棄は {@link ProgramContextAspect} が、 値の読み取りは {@link AuditProgramInterceptor} が担う。
 *
 * @see ProgramContextAspect
 * @see AuditProgramInterceptor
 */
public final class ProgramContext {

  private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

  private ProgramContext() {}

  /**
   * 空のときだけ設定し true(=owner) を返す。既に値が有れば設定せず false を返す。
   *
   * @param program 記録する機能識別子（{@code ClassName#method}）
   * @return このスレッドで最初に設定した（owner になった）場合のみ true
   */
  public static boolean setIfAbsent(String program) {
    if (HOLDER.get() == null) {
      HOLDER.set(program);
      return true;
    }
    return false;
  }

  /** 現在保持している機能識別子を返す。未設定なら null。 */
  public static String get() {
    return HOLDER.get();
  }

  /** ThreadLocal を破棄する。owner のみが finally で呼ぶ。 */
  public static void clear() {
    HOLDER.remove();
  }
}
