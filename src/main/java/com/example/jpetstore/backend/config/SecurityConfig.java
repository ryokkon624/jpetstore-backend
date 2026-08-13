package com.example.jpetstore.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * セキュリティ設定。secure-by-default 方針で、明示的に許可したパス以外はすべて認証を要求する。
 *
 * <p>疎通確認（{@code /api/ping}）・ヘルスチェック・OpenAPI ドキュメント/Swagger UI のみ permitAll とし、 それ以外は
 * authenticated。
 *
 * <p>TODO(Phase3): 本格的な認証は JWT を httpOnly Cookie に載せる方式で実装する。 それに伴い csrf の扱い（Cookie 認証なら CSRF
 * 対策が必要）・stateless セッション・ 認証フィルタの追加を再設計する。現状は雛形のため csrf / formLogin / httpBasic を一旦 disable している。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/ping", "/actuator/health", "/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        // TODO(Phase3): JWT httpOnly Cookie 認証の導入時に再設計する
        .csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable);
    return http.build();
  }
}
