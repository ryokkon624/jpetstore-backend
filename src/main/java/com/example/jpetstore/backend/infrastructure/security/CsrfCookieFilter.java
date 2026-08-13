package com.example.jpetstore.backend.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRF トークンの遅延解決を毎リクエストで強制するフィルタ（AC3・SBD-3。Spring Security 公式の SPA 向けパターン）。
 *
 * <p>Spring Security の {@code CsrfFilter} は {@link CsrfToken} を {@code Supplier} として遅延解決する ため、誰も
 * {@link CsrfToken#getToken()} を呼ばないと {@code CookieCsrfTokenRepository} が {@code XSRF-TOKEN} Cookie
 * を書き込まない。SPA は最初のリクエストで Cookie を得たいため、本フィルタが 明示的に {@code getToken()} を呼び解決を強制する。
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (csrfToken != null) {
      csrfToken.getToken();
    }
    filterChain.doFilter(request, response);
  }
}
