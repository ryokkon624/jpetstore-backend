package com.example.jpetstore.backend.config;

import com.example.jpetstore.backend.infrastructure.security.CsrfCookieFilter;
import com.example.jpetstore.backend.infrastructure.security.JwtAuthenticationFilter;
import com.example.jpetstore.backend.presentation.rest.security.AuditingAccessDeniedHandler;
import com.example.jpetstore.backend.presentation.rest.security.AuditingAuthenticationEntryPoint;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * <p>credential を交換するログイン API（{@code POST /api/auth/login}）・{@code UserDetailsService} の DB
 * 結線・{@code AuthenticationManager}（{@code DaoAuthenticationProvider}）の結線は #18 で追加した（#23 時点では
 * refresh のみの土台だったため、当時のコメントは #21 と誤記していたが実際は #18）。
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
                        // springdoc のエントリポイント "/swagger-ui.html" は "/swagger-ui/index.html" への
                        // リダイレクトであり "/swagger-ui/**" には一致しない。両方を許可する必要がある
                        // （Sprint Review 指摘で発覚）。
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        // AC3: refresh は credential 不要（refresh Cookie のみで判定）。
                        "/api/auth/refresh",
                        // #18 AC1/AC4: login/logout はそれ自体が認証の入口/出口のため未認証で到達可能にする
                        // （CSRF は下記 csrf() 設定で別途必須。認証チェック自体は免除するだけ）。
                        "/api/auth/login",
                        "/api/auth/logout")
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
        // メソッド参照(AbstractHttpConfigurer::disable)はnull型安全解析で警告が出るためラムダ化（挙動は不変）。
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable());
    return http.build();
  }

  /**
   * ログイン（#18）で credential（username/password）を照合するための {@link AuthenticationManager}。{@link
   * DaoAuthenticationProvider} に {@link UserDetailsService}（{@code JdbcUserDetailsService}）と {@link
   * PasswordEncoder}（#19 の {@code PasswordEncoderConfig}）を結線する。
   *
   * <p>未知の username（{@code UsernameNotFoundException}）は {@code DaoAuthenticationProvider} の既定動作
   * （{@code hideUserNotFoundExceptions=true}）により、誤 password と同一の {@code BadCredentialsException} に
   * 正規化される（SBD-6・列挙不可）。
   */
  @Bean
  public AuthenticationManager authenticationManager(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
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
