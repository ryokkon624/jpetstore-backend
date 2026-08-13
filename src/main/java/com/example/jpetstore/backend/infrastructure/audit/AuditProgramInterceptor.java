package com.example.jpetstore.backend.infrastructure.audit;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.stereotype.Component;

/**
 * INSERT / UPDATE 実行時に、対象エンティティの WHOカラム（{@code createProgram} / {@code updateProgram}）が 未設定なら
 * {@link ProgramContext} の値で補完する MyBatis インターセプタ。
 *
 * <p>{@code Executor#update} を intercept する。ThreadLocal が空（例: コントローラが業務サービスを 介さず直接 mapper
 * を呼んだ等）の場合は {@code "SYSTEM"} を記録する。既に明示設定された値は尊重する（set-once）。
 *
 * <p><b>注意:</b> {@code param} が Map の場合、MyBatis の MetaObject は存在しないキーに対しても setter を
 * 持つとみなし得るため、意図しない put が起こり得る。本インターセプタは MyBatis Generator が生成した エンティティ（POJO）を param とする利用を前提とする。
 *
 * <p>mybatis-spring-boot-starter は {@link Interceptor} 型の {@code @Component} Bean を自動登録するため、
 * 明示的な設定登録は不要。
 *
 * @see ProgramContext
 * @see ProgramContextAspect
 */
@Component
@Intercepts(
    @Signature(
        type = Executor.class,
        method = "update",
        args = {MappedStatement.class, Object.class}))
public class AuditProgramInterceptor implements Interceptor {

  private static final String FALLBACK_PROGRAM = "SYSTEM";

  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
    Object param = invocation.getArgs()[1];
    SqlCommandType type = ms.getSqlCommandType();

    if (param != null && (type == SqlCommandType.INSERT || type == SqlCommandType.UPDATE)) {
      String program = ProgramContext.get();
      if (program == null || program.isBlank()) {
        program = FALLBACK_PROGRAM;
      }

      MetaObject meta = SystemMetaObject.forObject(param);
      if (type == SqlCommandType.INSERT) {
        fillIfBlank(meta, "createProgram", program);
      }
      // INSERT / UPDATE 双方で updateProgram を補完する
      fillIfBlank(meta, "updateProgram", program);
    }

    return invocation.proceed();
  }

  /** 対象プロパティが未設定（null または空白文字列）のときだけ value を設定する。 */
  private void fillIfBlank(MetaObject meta, String prop, String value) {
    if (meta.hasSetter(prop) && meta.hasGetter(prop)) {
      Object cur = meta.getValue(prop);
      if (cur == null || (cur instanceof String s && s.isBlank())) {
        meta.setValue(prop, value);
      }
    }
  }
}
