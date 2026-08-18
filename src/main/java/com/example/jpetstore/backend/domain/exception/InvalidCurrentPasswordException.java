package com.example.jpetstore.backend.domain.exception;

/**
 * パスワード変更（#15）時、送信された現在パスワードが本人の格納ハッシュと一致しないことを表す。
 *
 * <p>{@code GlobalExceptionHandler}が422 Unprocessable Entityへ正規化する（計画フェーズ確定・三系統分離）。
 * 弱いパスワード/不正入力（Bean Validation）＝400、真の未認証＝401、CSRF欠落＝403とはステータスを分ける （401は{@code httpClient}のsilent
 * refreshを誤発火させ、403はCSRF欠落と衝突するため）。
 */
public class InvalidCurrentPasswordException extends RuntimeException {

  private static final String DEFAULT_MESSAGE = "Current password is incorrect.";

  public InvalidCurrentPasswordException() {
    super(DEFAULT_MESSAGE);
  }
}
