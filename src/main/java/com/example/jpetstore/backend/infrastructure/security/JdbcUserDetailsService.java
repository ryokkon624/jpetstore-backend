package com.example.jpetstore.backend.infrastructure.security;

import com.example.jpetstore.backend.domain.security.AuthenticatedUser;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.entity.AccountAuthCustomEntity;
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.AccountAuthCustomMapper;
import java.util.List;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * {@code m_account}×{@code m_signon} を参照する {@link UserDetailsService} 実装（#18）。
 *
 * <p>{@code DaoAuthenticationProvider} から呼ばれる。存在しない username は {@link UsernameNotFoundException} を
 * 投げるが、{@code DaoAuthenticationProvider} の既定動作（{@code hideUserNotFoundExceptions=true}）により
 * 呼び出し元には誤PWと同一の {@code BadCredentialsException} として正規化される（SBD-6・列挙不可）。
 *
 * <p>ロールはスキーマに role/authority 表が無いため既定 {@link #DEFAULT_ROLES}（{@code ["USER"]}）を付与する。role
 * 表導入時にここを差し替える。
 */
@Component
public class JdbcUserDetailsService implements UserDetailsService {

  private static final List<String> DEFAULT_ROLES = List.of("USER");

  private final AccountAuthCustomMapper mapper;

  public JdbcUserDetailsService(AccountAuthCustomMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    AccountAuthCustomEntity entity = mapper.findByUsername(username);
    if (entity == null) {
      throw new UsernameNotFoundException("User not found: " + username);
    }
    AuthenticatedUser user =
        new AuthenticatedUser(entity.getUserId(), entity.getUsername(), DEFAULT_ROLES);
    return new AuthenticatedUserDetails(user, entity.getPasswordHash());
  }
}
