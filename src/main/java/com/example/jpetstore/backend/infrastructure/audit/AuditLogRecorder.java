package com.example.jpetstore.backend.infrastructure.audit;

import com.example.jpetstore.backend.domain.security.AuthenticatedUser;
import com.example.jpetstore.backend.domain.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 監査ログ（{@code t_audit_log}）の記録機構（AC7・SBD-14）。
 *
 * <p>本 Story のスコープは記録機構そのものと、認可失敗（401/403）の記録結線まで。状態変更 （注文作成・account 編集等）の実際の呼び出し箇所は後続ドメイン Story が
 * {@link #recordStateChange} を呼び出す想定。
 */
@Component
public class AuditLogRecorder {

  private static final String EVENT_AUTHZ_FAILURE = "AUTHZ_FAILURE";
  private static final String EVENT_STATE_CHANGE = "STATE_CHANGE";
  private static final String RESULT_DENIED = "DENIED";

  private final AuditLogMapper mapper;
  private final CurrentUserProvider currentUserProvider;
  private final ObjectMapper objectMapper;

  public AuditLogRecorder(
      AuditLogMapper mapper, CurrentUserProvider currentUserProvider, ObjectMapper objectMapper) {
    this.mapper = mapper;
    this.currentUserProvider = currentUserProvider;
    this.objectMapper = objectMapper;
  }

  /**
   * 認可失敗（401/403）を記録する。{@code AccessDeniedHandler}/{@code AuthenticationEntryPoint} から
   * 呼ばれる想定。SecurityContext が空（未認証リクエストの拒否）の場合、actor は NULL になる （認可失敗時に ProgramContext も空になるため
   * create_program は "SYSTEM" が補完される。許容仕様）。
   */
  public void recordAuthzFailure(String action, String reason, HttpServletRequest request) {
    AuthenticatedUser actor = currentUserProvider.currentUser().orElse(null);
    insert(
        EVENT_AUTHZ_FAILURE,
        actor,
        action,
        null,
        null,
        RESULT_DENIED,
        reason == null ? null : java.util.Map.of("reason", reason),
        clientIp(request));
  }

  /** 状態変更（注文作成・account 編集等）を記録する。後続ドメイン Story がユースケースの呼び出し箇所で 呼ぶ想定（本 Story ではこの API を用意するところまで）。 */
  public void recordStateChange(
      String action, String targetType, String targetId, String result, Object detail) {
    AuthenticatedUser actor = currentUserProvider.currentUser().orElse(null);
    insert(EVENT_STATE_CHANGE, actor, action, targetType, targetId, result, detail, null);
  }

  private void insert(
      String eventType,
      AuthenticatedUser actor,
      String action,
      String targetType,
      String targetId,
      String result,
      Object detail,
      String clientIp) {
    AuditLogEntity entity = new AuditLogEntity();
    entity.setEventType(eventType);
    entity.setActorUserId(actor == null ? null : actor.userId());
    entity.setActorUsername(actor == null ? null : actor.username());
    entity.setAction(action);
    entity.setTargetType(targetType);
    entity.setTargetId(targetId);
    entity.setResult(result);
    entity.setDetail(toJson(detail));
    entity.setClientIp(clientIp);
    mapper.insert(entity);
  }

  private String toJson(Object detail) {
    if (detail == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(detail);
    } catch (JacksonException e) {
      return "{\"error\":\"failed to serialize audit detail\"}";
    }
  }

  /**
   * レビュー指摘対応（secure-by-default）: {@code X-Forwarded-For} はクライアントが自由に送れるヘッダであり、
   * 信頼できるリバースプロキシ構成（プロキシがヘッダを上書きする設定）が前提に無い限り無条件に信頼すると 監査ログの {@code client_ip}
   * を偽装されうる。現状は信頼プロキシ構成が無いため、常に {@code request.getRemoteAddr()} を使う。
   *
   * <p>TODO: リバースプロキシ配下で運用する場合（infra/後続対応）、信頼できるプロキシの実 IP からの リクエストに限り {@code X-Forwarded-For}
   * の最左端（オリジナルクライアント）を採用する方式へ拡張する。
   */
  private String clientIp(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    return request.getRemoteAddr();
  }
}
