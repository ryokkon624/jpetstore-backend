package com.example.jpetstore.backend.infrastructure.security;

import com.example.jpetstore.backend.domain.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * httpOnly Cookie の access token を検証し、成功すれば {@link SecurityContextHolder} に認証情報をセットする
 * フィルタ（AC3・SBD-3/4/15）。
 *
 * <p>Cookie が無い/検証失敗の場合は何もセットせずチェーンを継続する（401/403 の判定は 後段の認可（{@code
 * authorizeHttpRequests}/{@code @PreAuthorize}）に委ねる）。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final AuthCookieSupport cookieSupport;

  public JwtAuthenticationFilter(JwtService jwtService, AuthCookieSupport cookieSupport) {
    this.jwtService = jwtService;
    this.cookieSupport = cookieSupport;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    cookieSupport
        .readCookie(request, AuthCookieSupport.ACCESS_TOKEN_COOKIE)
        .flatMap(jwtService::parseAccessToken)
        .ifPresent(this::authenticate);
    filterChain.doFilter(request, response);
  }

  private void authenticate(AuthenticatedUser user) {
    List<GrantedAuthority> authorities =
        user.roles().stream()
            .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList();
    var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
