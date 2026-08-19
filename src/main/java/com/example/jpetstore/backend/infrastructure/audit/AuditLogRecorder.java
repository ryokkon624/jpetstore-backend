package com.example.jpetstore.backend.infrastructure.audit;

import com.example.jpetstore.backend.domain.security.AuthenticatedUser;
import com.example.jpetstore.backend.domain.security.CurrentUserProvider;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AuditLogCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.AuditLogCustomMapper;
import com.example.jpetstore.backend.infrastructure.security.AuditWriteQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 監査ログ（{@code t_audit_log}）の記録機構（AC7・SBD-14）。
 *
 * <p>本 Story のスコープは記録機構そのものと、認可失敗（401/403）の記録結線まで。状態変更 （注文作成・account 編集等）の実際の呼び出し箇所は後続ドメイン Story が
 * {@link #recordStateChange} を呼び出す想定。
 *
 * <p>Mapper/Entity は {@code infrastructure.mybatis.custom} 配下の {@link AuditLogCustomMapper}/{@link
 * AuditLogCustomEntity}（カスタムマッパー規約準拠）。本クラス自身は監査という横断関心のファサードとして {@code
 * infrastructure.audit}（WHO自動付与と同じパッケージ）に留める。
 *
 * <p><b>#39 AC1（truncate）</b>: {@link #insert} で {@code action}/{@code targetType}/{@code
 * targetId}/{@code actorUsername} を列幅内へ先頭側保持で切り詰める。AC1 が要求するのは {@code action} のみだが、他3列も同じ経路で INSERT
 * 失敗しうるため意図的な防御多層化として3列とも対象にした（実装ノート参照）。
 *
 * <p><b>#39 AC2（best-effort）</b>: {@link #insert} 内の {@code mapper.insert} を例外保護し、記録失敗が
 * 呼び出し元（セキュリティハンドラ等）へ伝播しないようにする。ただし SBD-14「記録する」宣言に対する後退を 最小化するため、握り潰さず必ずアプリログへ ERROR で残す（S2・{@code
 * recordStateChange} の成功監査にも及ぶ）。{@link #isWithinQuota} の quota チェック自体の例外も同じく best-effort
 * （fail-open）で保護する（SM verification 確定所見: quota チェックの例外を素通りさせると、それ自体が
 * セキュリティハンドラ内からの例外送出→`/error`ディスパッチという N2 と同一の失敗モードになってしまうため）。
 */
@Component
public class AuditLogRecorder {

  private static final Logger log = LoggerFactory.getLogger(AuditLogRecorder.class);

  private static final String EVENT_AUTHZ_FAILURE = "AUTHZ_FAILURE";
  private static final String EVENT_STATE_CHANGE = "STATE_CHANGE";
  private static final String RESULT_DENIED = "DENIED";

  private static final int ACTION_MAX_LENGTH = 100;
  private static final int TARGET_TYPE_MAX_LENGTH = 50;
  private static final int TARGET_ID_MAX_LENGTH = 50;
  private static final int ACTOR_USERNAME_MAX_LENGTH = 80;

  private final AuditLogCustomMapper mapper;
  private final CurrentUserProvider currentUserProvider;
  private final ObjectMapper objectMapper;
  private final AuditWriteQuotaService auditWriteQuotaService;

  public AuditLogRecorder(
      AuditLogCustomMapper mapper,
      CurrentUserProvider currentUserProvider,
      ObjectMapper objectMapper,
      AuditWriteQuotaService auditWriteQuotaService) {
    this.mapper = mapper;
    this.currentUserProvider = currentUserProvider;
    this.objectMapper = objectMapper;
    this.auditWriteQuotaService = auditWriteQuotaService;
  }

  /**
   * 認可失敗（401/403）を記録する。{@code AccessDeniedHandler}/{@code AuthenticationEntryPoint} から
   * 呼ばれる想定。SecurityContext が空（未認証リクエストの拒否）の場合、actor は NULL になる （認可失敗時に ProgramContext も空になるため
   * create_program は "SYSTEM" が補完される。許容仕様）。
   *
   * <p>#39 AC3（N14）: 未認証（actor==null）由来の write のみ {@link AuditWriteQuotaService} で窓内上限を
   * 掛ける。認証済みの403は無制限に記録する（理由は {@link AuditWriteQuotaService} javadoc 参照）。quota
   * チェック自体が例外を投げても本メソッドは正常復帰する（{@link #isWithinQuota} 参照）。
   */
  public void recordAuthzFailure(String action, String reason, HttpServletRequest request) {
    AuthenticatedUser actor = currentUserProvider.currentUser().orElse(null);
    String clientIp = clientIp(request);
    if (actor == null && clientIp != null && !isWithinQuota(clientIp)) {
      return; // 抑止(AuditWriteQuotaServiceがsuppressed_count+WARNログを記録済み・黙って消えない)
    }
    insert(
        EVENT_AUTHZ_FAILURE,
        actor,
        action,
        null,
        null,
        RESULT_DENIED,
        reason == null ? null : java.util.Map.of("reason", reason),
        clientIp);
  }

  /**
   * {@link AuditWriteQuotaService#tryAcquire} を呼び、枠が確保できたか（quota 内か）を返す。
   *
   * <p><b>SM verification 確定所見（#39 AC2 未達の是正）</b>: {@code tryAcquire} は
   * {@code @Transactional(REQUIRES_NEW)} で新規コネクション取得を伴うため、未認証フラッド時（＝まさに quota
   * が守ろうとしている状況）にコネクションプール枯渇等で例外を投げやすい。これを素通りさせると、 {@link #recordAuthzFailure} の唯一の呼び出し元（{@code
   * AuditingAccessDeniedHandler}/{@code AuditingAuthenticationEntryPoint}/{@code
   * GlobalExceptionHandler}）内から例外が伝播し、{@code /error} への ERROR ディスパッチ経由で 403/401 が化ける・監査も残らない、という
   * **#39 が修正対象にしている N2 と同一の失敗モード**を、トリガを「過大長URI」から「quotaのDBエラー」に変えただけで再現してしまう。
   *
   * <p>そのため **fail-open** とする: quota チェック自体が失敗した場合は「枠あり」とみなして監査記録へ進む （黙って落とさずアプリログへ ERROR
   * は残す）。quota は可用性のための緩和策（N14 対策）にすぎず、監査記録 そのものは SBD-14 が要求するセキュリティ統制であるため、可用性側の失敗でセキュリティ側の記録を止める
   * （fail-closed で return する）のは優先順位が逆転する。fail-closed にすると、quota の DB 障害がそのまま 新しい監査抑止経路になってしまう点にも注意。
   */
  private boolean isWithinQuota(String clientIp) {
    try {
      return auditWriteQuotaService.tryAcquire(clientIp);
    } catch (RuntimeException e) {
      log.error(
          "Audit write quota check failed; proceeding with audit record (fail-open, clientIp={})",
          clientIp,
          e);
      return true;
    }
  }

  /** 状態変更（注文作成・account 編集等）を記録する。後続ドメイン Story がユースケースの呼び出し箇所で 呼ぶ想定（本 Story ではこの API を用意するところまで）。 */
  public void recordStateChange(
      String action, String targetType, String targetId, String result, Object detail) {
    AuthenticatedUser actor = currentUserProvider.currentUser().orElse(null);
    insert(EVENT_STATE_CHANGE, actor, action, targetType, targetId, result, detail, null);
  }

  /**
   * 状態変更の失敗（在庫不足・空カート等）を、呼び出し元の主トランザクションから独立した別トランザクションで 記録する（#8 AC6・失敗監査）。
   *
   * <p>{@link Propagation#REQUIRES_NEW} により、呼び出し元（例: {@code
   * OrderApplicationService#placeOrder}）の主トランザクションが最終的にロールバックされても、この監査行は
   * 別コミットとして残る。呼び出し元は本メソッドを持つ本クラス（別bean）を経由して呼ぶため、Spring AOP
   * プロキシが正しく介在し（自己呼び出しではない）、{@code @Transactional} が効く。
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordStateChangeIndependently(
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
    AuditLogCustomEntity entity = new AuditLogCustomEntity();
    entity.setEventType(eventType);
    entity.setActorUserId(actor == null ? null : actor.userId());
    entity.setActorUsername(
        truncate(actor == null ? null : actor.username(), ACTOR_USERNAME_MAX_LENGTH));
    entity.setAction(truncate(action, ACTION_MAX_LENGTH));
    entity.setTargetType(truncate(targetType, TARGET_TYPE_MAX_LENGTH));
    entity.setTargetId(truncate(targetId, TARGET_ID_MAX_LENGTH));
    entity.setResult(result);
    entity.setDetail(toJson(detail));
    entity.setClientIp(clientIp);
    try {
      mapper.insert(entity);
    } catch (RuntimeException e) {
      // #39 AC2: 記録失敗を呼び出し元(セキュリティハンドラ等)へ伝播させない(best-effort)。
      // SBD-14の記録宣言に対する後退を最小化するため、握り潰さず必ずアプリログへERRORで残す(S2)。
      log.error(
          "Failed to record audit log (eventType={}, action={}, targetType={}, targetId={})",
          eventType,
          entity.getAction(),
          entity.getTargetType(),
          entity.getTargetId(),
          e);
    }
  }

  /** {@code value} の先頭 {@code maxLength} 文字だけを残す（#39 AC1・DB列幅への溢れを防ぐ）。null はそのまま null。 */
  private static String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
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
