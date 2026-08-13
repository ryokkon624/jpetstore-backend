package com.example.jpetstore.backend.domain.security;

import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;

/**
 * 現在の認証プリンシパルを取得するための取得口（AC2・SBD-1）。
 *
 * <p>サービス/ドメイン層は、リクエストパラメータ（例: {@code request.userId}）ではなく本インタフェース
 * を介して認証プリンシパルを取得しなければならない。実装（{@code SecurityContextCurrentUserProvider}）は Infrastructure
 * 層に置き、Spring Security の {@code SecurityContextHolder} に依存する詳細を隠蔽する。
 */
public interface CurrentUserProvider {

  /** 現在の認証プリンシパルを返す。未認証なら {@link Optional#empty()}。 */
  Optional<AuthenticatedUser> currentUser();

  /** 現在の認証プリンシパルを返す。未認証なら {@link AccessDeniedException} を投げる。 */
  default AuthenticatedUser requireCurrentUser() {
    return currentUser().orElseThrow(() -> new AccessDeniedException("Authentication required"));
  }
}
