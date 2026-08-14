package com.example.jpetstore.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * DB 資格情報が確実に環境変数から解決されていることを起動時に強制検証するためのマーカー Bean。
 *
 * <p>AC5/AC-neg1 (SBD-11): {@code spring.datasource.username}/{@code password} は
 * {@code @ConfigurationProperties}（Binder）経由の Spring Boot 標準バインディングでは、値が {@code ${DB_USERNAME}}
 * のような未解決プレースホルダのままでも例外を投げず、リテラル文字列としてそのまま 通ってしまう（実際に DB へ接続を試みて初めて認証エラーとして顕在化する。起動時点では気づけない）。
 *
 * <p>これは「設定し忘れても安全（secure-by-default）」に反するため、{@code @Value} による即時 プレースホルダ解決をこの Bean
 * のコンストラクタで強制する。{@code DB_USERNAME}/{@code DB_PASSWORD} が未設定なら {@link
 * org.springframework.util.PlaceholderResolutionException} で ApplicationContext
 * の起動自体が失敗する（fail-fast）。値そのものは保持しない。
 */
@Component
class RequiredSecretsValidator {

  RequiredSecretsValidator(
      @Value("${spring.datasource.username}") String dbUsername,
      @Value("${spring.datasource.password}") String dbPassword) {
    // コンストラクタ引数として @Value 解決させるだけで目的を達する。
    // フィールドに保持しない（秘密をメモリ上に不要に残さない）。
  }
}
