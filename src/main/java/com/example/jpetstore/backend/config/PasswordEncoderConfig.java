package com.example.jpetstore.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * パスワードをハッシュ＋ソルトで保存・照合する基盤（AC1・SBD-5）。
 *
 * <p>Spring Security 標準の {@link PasswordEncoderFactories#createDelegatingPasswordEncoder()} を採用する。
 * bcrypt を既定アルゴリズムとして {@code {bcrypt}} プレフィックス付きでエンコードし、{@code matches} 時はプレフィックスから
 * アルゴリズムを判定して照合する（将来アルゴリズムを移行する場合も既存ハッシュを壊さず段階移行できる、Spring Security 公式の推奨方式）。
 *
 * <p>{@code m_signon.password_hash varchar(255)} は bcrypt のプレフィックス込みハッシュ（60文字前後＋プレフィックス）を
 * 十分に格納できる長さ（#22 で確保済み）。
 */
@Configuration
public class PasswordEncoderConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }
}
