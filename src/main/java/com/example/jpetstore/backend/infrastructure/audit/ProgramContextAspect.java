package com.example.jpetstore.backend.infrastructure.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 業務サービス層（{@code ..application.service..}）の全メソッドを囲み、 WHOカラムに記録する機能識別子（{@code ClassName#method}）を {@link
 * ProgramContext} にセットするアスペクト。
 *
 * <p>「最外の業務サービスが勝つ」set-once 方式。enter で {@link ProgramContext#setIfAbsent(String)} を呼び、 空だった場合のみ owner
 * となる。owner は finally で {@link ProgramContext#clear()} する。 ネストした内側のサービス呼び出しは owner ではないため、ThreadLocal
 * を触らない。
 *
 * <p>これにより {@code OrderService#placeOrder} → {@code CommonWriteService.insert()} と潜っても、 記録されるのは最外の {@code
 * OrderService#placeOrder}（＝機能の入口）になる。
 *
 * @see ProgramContext
 * @see AuditProgramInterceptor
 */
@Aspect
@Component
public class ProgramContextAspect {

  @Around("execution(* com.example.jpetstore.backend.application.service..*.*(..))")
  public Object aroundServiceMethod(ProceedingJoinPoint pjp) throws Throwable {
    String className = pjp.getSignature().getDeclaringType().getSimpleName();
    String methodName = pjp.getSignature().getName();
    boolean owner = ProgramContext.setIfAbsent(className + "#" + methodName);
    try {
      return pjp.proceed();
    } finally {
      if (owner) {
        ProgramContext.clear();
      }
    }
  }
}
