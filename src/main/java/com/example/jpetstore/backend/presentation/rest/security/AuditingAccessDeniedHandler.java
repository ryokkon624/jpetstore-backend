package com.example.jpetstore.backend.presentation.rest.security;

import com.example.jpetstore.backend.infrastructure.audit.AuditLogRecorder;
import com.example.jpetstore.backend.presentation.rest.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 認可失敗（403）を正規化 JSON で返し、監査ログに記録する（AC3/AC4/AC7・SBD-10/SBD-14）。
 *
 * <p>{@code ExceptionTranslationFilter}（Spring Security フィルタチェーン）が拾った {@link AccessDeniedException}
 * のエントリポイント。DispatcherServlet の外側で発生するため {@code
 * GlobalExceptionHandler}（{@code @RestControllerAdvice}）は経由しない。
 */
@Component
public class AuditingAccessDeniedHandler implements AccessDeniedHandler {

  private final AuditLogRecorder auditLogRecorder;
  private final ObjectMapper objectMapper;

  public AuditingAccessDeniedHandler(AuditLogRecorder auditLogRecorder, ObjectMapper objectMapper) {
    this.auditLogRecorder = auditLogRecorder;
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    auditLogRecorder.recordAuthzFailure(
        request.getRequestURI(), accessDeniedException.getMessage(), request);

    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    ErrorResponse body = ErrorResponse.of("FORBIDDEN", "Access is denied", request.getRequestURI());
    response.getWriter().write(objectMapper.writeValueAsString(body));
  }
}
