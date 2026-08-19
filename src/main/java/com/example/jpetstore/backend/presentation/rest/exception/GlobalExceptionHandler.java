package com.example.jpetstore.backend.presentation.rest.exception;

import com.example.jpetstore.backend.domain.exception.InsufficientStockException;
import com.example.jpetstore.backend.domain.exception.InvalidCurrentPasswordException;
import com.example.jpetstore.backend.domain.exception.OptimisticLockConflictException;
import com.example.jpetstore.backend.domain.exception.RegistrationRateLimitExceededException;
import com.example.jpetstore.backend.domain.exception.ResourceNotFoundException;
import com.example.jpetstore.backend.domain.exception.UsernameAlreadyExistsException;
import com.example.jpetstore.backend.infrastructure.audit.AuditLogRecorder;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

  /**
   * 注文確定時の在庫不足・空カート（#8 AC2・計画フェーズ確定①）を 409 Conflict として正規化する。既存の {@link
   * OptimisticLockConflictException}=409 と系を揃える。
   */
  @ExceptionHandler(InsufficientStockException.class)
  public ResponseEntity<ErrorResponse> handleInsufficientStock(
      InsufficientStockException e, HttpServletRequest request) {
    return build(HttpStatus.CONFLICT, "CONFLICT", e.getMessage(), request);
  }

  /**
   * ユーザー登録時のusername重複（#13 E4）を409 Conflict＋明示メッセージとして正規化する。既存の {@link
   * OptimisticLockConflictException}=409 と系を揃える（列挙対策はレート制限が担保する前提のため、メッセージを あえて明示的にする＝E4計画確定）。
   */
  @ExceptionHandler(UsernameAlreadyExistsException.class)
  public ResponseEntity<ErrorResponse> handleUsernameAlreadyExists(
      UsernameAlreadyExistsException e, HttpServletRequest request) {
    return build(HttpStatus.CONFLICT, "CONFLICT", e.getMessage(), request);
  }

  /** ユーザー登録エンドポイントのレート制限超過（#13 AC4/AC-neg2・SBD-6）を429 Too Many Requestsとして正規化する。 */
  @ExceptionHandler(RegistrationRateLimitExceededException.class)
  public ResponseEntity<ErrorResponse> handleRegistrationRateLimitExceeded(
      RegistrationRateLimitExceededException e, HttpServletRequest request) {
    return build(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", e.getMessage(), request);
  }

  /**
   * パスワード変更時の現在パスワード誤り（#15 AC1）を422 Unprocessable Entityとして正規化する（計画フェーズ確定・三系統分離）。 弱いパスワード/不正入力（Bean
   * Validation）＝400、真の未認証＝401、CSRF欠落＝403とはステータスを分ける（401はhttpClientのsilent refreshを
   * 誤発火させ、403はCSRF欠落と衝突するため）。
   */
  @ExceptionHandler(InvalidCurrentPasswordException.class)
  public ResponseEntity<ErrorResponse> handleInvalidCurrentPassword(
      InvalidCurrentPasswordException e, HttpServletRequest request) {
    // Spring Framework 7.0でHttpStatus.UNPROCESSABLE_ENTITYは非推奨化され、RFC 9110の呼称に合わせた
    // UNPROCESSABLE_CONTENTへ改名された（値=422は不変）。
    return build(HttpStatus.UNPROCESSABLE_CONTENT, "UNPROCESSABLE_ENTITY", e.getMessage(), request);
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

  /**
   * #40 AC4(N11): AC2/AC3の入口検証（{@code @Size}/{@code @Valid}カスケード）をすり抜けた想定外の DB 制約違反（列幅超過・NOT
   * NULL違反等）を400として正規化する。専用ハンドラが無いと下の {@link #handleUnexpected}
   * が拾い500に丸めてしまう。DB由来の生メッセージは外部に返さず内部ログWARNのみに残す
   * （SBD-10）。防御多層（AC2/AC3の入口検証を通過した経路からは通常到達しない）。#42(D)（{@code favoriteCategoryId}
   * の実在検証欠落によるFK違反500）が本ハンドラを再利用する前提（PO判断・2026-08-19）。
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
      DataIntegrityViolationException e, HttpServletRequest request) {
    log.warn("Data integrity violation: {}", e.getMessage());
    return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid request data", request);
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
