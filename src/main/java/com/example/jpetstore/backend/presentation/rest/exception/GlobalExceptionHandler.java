package com.example.jpetstore.backend.presentation.rest.exception;

import com.example.jpetstore.backend.domain.exception.OptimisticLockConflictException;
import com.example.jpetstore.backend.domain.exception.ResourceNotFoundException;
import com.example.jpetstore.backend.infrastructure.audit.AuditLogRecorder;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 正規化エラーハンドリング基盤（AC4・SBD-10）。
 *
 * <p>例外はスタックトレース・内部パス（実装クラス/ファイルパス）・依存ライブラリの版数を露出しない {@link ErrorResponse} にマッピングする。詳細は内部ログにのみ残す。
 *
 * <p>Spring Security の認可失敗（{@link AccessDeniedException}）・未認証（{@link AuthenticationException}）は、URL
 * パターン単位の認可（{@code authorizeHttpRequests}）による拒否なら フィルタチェーン側（{@code ExceptionTranslationFilter} →
 * {@code AccessDeniedHandler}/{@code
 * AuthenticationEntryPoint}）が先に捕捉するが、メソッドセキュリティ（{@code @PreAuthorize}）由来の場合は DispatcherServlet
 * のディスパッチ内で発生し、Spring MVC の例外解決（＝ここ）が先に捕捉してしまい フィルタチェーン側へは伝播しない（実機検証で判明）。そのためどちらの経路でも監査ログ（AC7）が
 * 記録されるよう、ここでも {@link AuditLogRecorder#recordAuthzFailure} を呼ぶ。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final AuditLogRecorder auditLogRecorder;

  public GlobalExceptionHandler(AuditLogRecorder auditLogRecorder) {
    this.auditLogRecorder = auditLogRecorder;
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(
      ResourceNotFoundException e, HttpServletRequest request) {
    return build(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage(), request);
  }

  @ExceptionHandler(OptimisticLockConflictException.class)
  public ResponseEntity<ErrorResponse> handleConflict(
      OptimisticLockConflictException e, HttpServletRequest request) {
    return build(HttpStatus.CONFLICT, "CONFLICT", e.getMessage(), request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(
      AccessDeniedException e, HttpServletRequest request) {
    // AC4/SBD-10: 例外メッセージに内部詳細が入っていても外部には固定文言のみ返す。
    log.warn("Access denied: {}", e.getMessage());
    auditLogRecorder.recordAuthzFailure(request.getRequestURI(), e.getMessage(), request);
    return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access is denied", request);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleAuthentication(
      AuthenticationException e, HttpServletRequest request) {
    auditLogRecorder.recordAuthzFailure(request.getRequestURI(), e.getMessage(), request);
    return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required", request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException e, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Validation failed", request);
  }

  /**
   * リクエストボディが宣言型へデシリアライズできない（非数値・不正JSON等の型不一致）場合を 400 として正規化する（#5 AC2・SBD-10）。専用ハンドラが無いと下の {@link
   * #handleUnexpected} が拾い 500 に丸めてしまう（例: {@code UpdateCartItemRequest.quantity} に文字列 {@code "abc"}
   * を送る等）。
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleMessageNotReadable(
      HttpMessageNotReadableException e, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Malformed request body", request);
  }

  /**
   * マッピングされていない HTTP メソッドでのアクセス（AC-neg2: GET での状態変更エンドポイント呼び出し等）を 405 として正規化する。専用ハンドラが無いと下の {@link
   * #handleUnexpected} が拾い 500 に丸めてしまうため明示的に用意する。
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
      HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
    return build(
        HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed", request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(
      IllegalArgumentException e, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage(), request);
  }

  /**
   * クエリ/パスパラメータが宣言型へ変換できない（非数値・桁あふれ等の型不一致）場合を 400 として正規化する（#3 AC-neg1・SBD-10）。専用ハンドラが無いと下の {@link
   * #handleUnexpected} が拾い 500 に丸めてしまう。
   *
   * <p>方針統一（#3 DEV計画）: 型として変換できない入力のみをここで 400 にする。変換自体は成功するが範囲外の値（例: {@code page=9999}）は {@link
   * com.example.jpetstore.backend.domain.common.PageRequest} 側でクランプし空 200 のまま返す。
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException e, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid parameter type", request);
  }

  /**
   * 必須クエリパラメータの欠落を 400 として正規化する（#3 AC-neg1・SBD-10）。専用ハンドラが無いと下の {@link #handleUnexpected} が拾い 500
   * に丸めてしまう。
   */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> handleMissingParameter(
      MissingServletRequestParameterException e, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Missing required parameter", request);
  }

  /**
   * どのハンドラマッピング・静的リソースにも一致しない未知パスへのアクセスを 404 として正規化する（#3 AC-neg1・SBD-10）。専用ハンドラが無いと下の {@link
   * #handleUnexpected} が拾い 500 に丸めてしまう。
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoResourceFound(
      NoResourceFoundException e, HttpServletRequest request) {
    return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
    // AC4/SBD-10: 想定外の例外はスタックトレース/クラス名/メッセージを外部に出さない。内部ログにのみ残す。
    log.error("Unexpected error handling request {}", request.getRequestURI(), e);
    return build(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error occurred", request);
  }

  private ResponseEntity<ErrorResponse> build(
      HttpStatus status, String code, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(ErrorResponse.of(code, message, request.getRequestURI()));
  }
}
