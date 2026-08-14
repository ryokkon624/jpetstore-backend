package com.example.jpetstore.backend.infrastructure.security;

import com.example.jpetstore.backend.domain.security.AuthenticatedUser;
import com.example.jpetstore.backend.domain.security.CurrentUserProvider;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * {@link CurrentUserProvider} の実装。Spring Security の {@code SecurityContextHolder} から {@link
 * AuthenticatedUser} を取得する（AC2・SBD-1）。
 *
 * <p>{@link JwtAuthenticationFilter} が {@code Authentication#getPrincipal()} に {@link
 * AuthenticatedUser} を直接セットする前提。
 */
@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

  @Override
  public Optional<AuthenticatedUser> currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      return Optional.empty();
    }
    if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
      return Optional.of(user);
    }
    return Optional.empty();
  }
}
