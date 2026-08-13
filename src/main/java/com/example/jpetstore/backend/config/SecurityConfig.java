package com.example.jpetstore.backend.config;

import com.example.jpetstore.backend.infrastructure.security.CsrfCookieFilter;
import com.example.jpetstore.backend.infrastructure.security.JwtAuthenticationFilter;
import com.example.jpetstore.backend.presentation.rest.security.AuditingAccessDeniedHandler;
import com.example.jpetstore.backend.presentation.rest.security.AuditingAuthenticationEntryPoint;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * セキュリティ設定。secure-by-default 方針で、明示的に許可したパス以外はすべて認証を要求する （AC2/AC3・SBD-1/SBD-3/SBD-4/SBD-15）。
 *
 * <p>認証は JWT を httpOnly Cookie に載せる方式（stateless セッション）。Cookie 方式のため CSRF （SBD-3）を有効化する。SPA 向けに
 * {@code XSRF-TOKEN} Cookie を発行し（{@link CookieCsrfTokenRepository#withHttpOnlyFalse()}）、raw
 * トークンをそのままヘッダで送り返す想定 （{@link CsrfTokenRequestAttributeHandler}。既定の {@code
 * XorCsrfTokenRequestAttributeHandler} はヘッダ値のマスクを要求するため cookie-to-header パターンと相性が悪く不採用。公式ガイド準拠）。
 *
 * <p>credential を交換するログイン API・{@code UserDetailsService} の DB 結線は #21 の範囲。本 Story
 * は「認可を強制する仕組み」「認証プリンシパルの取得口」「JWT 発行/検証・Cookie 読み書き・ refresh」を土台として用意する。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationFilter jwtAuthenticationFilter,
      AuditingAccessDeniedHandler accessDeniedHandler,
      AuditingAuthenticationEntryPoint authenticationEntryPoint)
      throws Exception {
    CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/ping",
                        "/actuator/health",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        // AC3: refresh は credential 不要（refresh Cookie のみで判定）。
                        "/api/auth/refresh")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(csrfRequestHandler))
        .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(
            eh ->
                eh.accessDeniedHandler(accessDeniedHandler)
                    .authenticationEntryPoint(authenticationEntryPoint))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable);
    return http.build();
  }

  /**
   * {@link JwtAuthenticationFilter} は {@code @Component} のため、Spring Boot の既定動作では
   * サーブレットコンテナのフィルタとしても自動登録され、Spring Security のフィルタチェーンと合わせて 二重実行されてしまう。Security
   * チェーン内でのみ実行させるため自動登録を無効化する（Spring Boot のよく知られた作法）。
   */
  @Bean
  public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
      JwtAuthenticationFilter filter) {
    FilterRegistrationBean<JwtAuthenticationFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
