package com.example.jpetstore.backend.presentation.rest.exception;

import java.time.Instant;

/**
 * 正規化エラーレスポンス（AC4・SBD-10）。
 *
 * <p>スタックトレース・内部パス（ファイルパス/クラス実装詳細）・依存ライブラリの版数は含めない。 {@code path} はリクエストされた
 * URI（アプリの外部仕様として公開済みの情報であり内部実装パスではない）。
 */
public record ErrorResponse(String code, String message, String path, Instant timestamp) {

  public static ErrorResponse of(String code, String message, String path) {
    return new ErrorResponse(code, message, path, Instant.now());
  }
}
