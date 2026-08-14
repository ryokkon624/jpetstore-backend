package com.example.jpetstore.backend.presentation.rest.security;

import com.example.jpetstore.backend.infrastructure.audit.AuditLogRecorder;
import com.example.jpetstore.backend.presentation.rest.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 未認証アクセス（401）を正規化 JSON で返し、監査ログに記録する（AC3/AC4/AC7・SBD-10/SBD-14）。
 *
 * <p>認可失敗時は {@link com.example.jpetstore.backend.domain.security.CurrentUserProvider} が
 * 空を返すため（未認証）、記録される actor は NULL になる。{@code ProgramContext} も空のため {@code create_program} は "SYSTEM"
 * が補完される（許容仕様。README 参照）。
 */
@Component
public class AuditingAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final AuditLogRecorder auditLogRecorder;
  private final ObjectMapper objectMapper;

  public AuditingAuthenticationEntryPoint(
      AuditLogRecorder auditLogRecorder, ObjectMapper objectMapper) {
    this.auditLogRecorder = auditLogRecorder;
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    auditLogRecorder.recordAuthzFailure(
        request.getRequestURI(), authException.getMessage(), request);

    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    ErrorResponse body =
        ErrorResponse.of("UNAUTHORIZED", "Authentication is required", request.getRequestURI());
    response.getWriter().write(objectMapper.writeValueAsString(body));
  }
}
