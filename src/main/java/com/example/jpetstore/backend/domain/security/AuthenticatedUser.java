package com.example.jpetstore.backend.domain.security;

import java.util.List;

/**
 * 認証プリンシパル（AC2・SBD-1）。チャネル非依存で認可判定の基準とするドメインモデル。
 *
 * <p>Spring Security の {@code Authentication#getPrincipal()} にはこの型のインスタンスを直接格納する （{@code
 * UserDetails} や個々のリクエストパラメータには依存しない）。
 */
public record AuthenticatedUser(Long userId, String username, List<String> roles) {

  public AuthenticatedUser {
    roles = roles == null ? List.of() : List.copyOf(roles);
  }
}
