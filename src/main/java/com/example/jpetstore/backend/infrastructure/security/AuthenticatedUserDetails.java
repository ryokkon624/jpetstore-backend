package com.example.jpetstore.backend.infrastructure.security;

import com.example.jpetstore.backend.domain.security.AuthenticatedUser;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * {@link AuthenticatedUser} を Spring Security の {@link UserDetails} として表す認証専用アダプタ（#18）。
 *
 * <p>{@code DaoAuthenticationProvider}（{@link JdbcUserDetailsService}）が credential 照合の間だけ principal
 * として使う。ログイン成功後は {@link #authenticatedUser()} から標準の {@link AuthenticatedUser} を取り出し、 それを元に JWT
 * を発行する（保護リソースへのその後のアクセスは {@link JwtAuthenticationFilter} が {@code SecurityContextHolder} に {@link
 * AuthenticatedUser} を直接セットする既存の流儀を維持し、本クラスの型がそのまま流出することはない）。
 */
public class AuthenticatedUserDetails implements UserDetails {

  private final AuthenticatedUser authenticatedUser;
  private final String passwordHash;

  public AuthenticatedUserDetails(AuthenticatedUser authenticatedUser, String passwordHash) {
    this.authenticatedUser = authenticatedUser;
    this.passwordHash = passwordHash;
  }

  public AuthenticatedUser authenticatedUser() {
    return authenticatedUser;
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return authenticatedUser.username();
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authenticatedUser.roles().stream()
        .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
        .toList();
  }

  // アカウントロックアウト・有効期限は本 Story のスコープ外（現状は全ユーザー有効固定）。
  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
