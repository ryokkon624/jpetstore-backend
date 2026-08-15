package com.example.jpetstore.backend.domain.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * 本人スコープ（所有者一致）認可の再利用可能な部品（#21 AC1・SBD-1）。
 *
 * <p>{@link CurrentUserProvider} だけを identity 源とし、呼び出し元が渡す {@code resourceOwnerUserId}（対象リソースの真の所有者
 * userId。呼び出し元がサーバー側で解決済みの値。リクエストの form/param をそのまま渡してはならない）と比較する。不一致（または未認証）なら {@link
 * AccessDeniedException} を投げ、{@code GlobalExceptionHandler} が 403 に正規化しつつ監査ログに記録する（#21 AC2・SBD-14）。
 *
 * <p>本 Story のスコープは「土台の提供＋実証（{@code SecuredPingController}）」まで。各ドメイン（order/account 等）が対象リソースを実装した際、
 * リポジトリ等で解決した真の所有者 userId を渡してそのまま呼び出せる（{@code backend-conventions}
 * §5「リソース認可でクライアント入力のhouseholdIdを信頼しない」と同じ思想: 対象リソースIDからサーバー側で所有者を解決してから検証する）。
 */
@Component
public class OwnershipAuthorizationService {

  private final CurrentUserProvider currentUserProvider;

  public OwnershipAuthorizationService(CurrentUserProvider currentUserProvider) {
    this.currentUserProvider = currentUserProvider;
  }

  /**
   * 現在の認証プリンシパルが {@code resourceOwnerUserId} の所有者であることを検証する。
   *
   * @param resourceOwnerUserId 対象リソースの真の所有者 userId（サーバー側で解決済みの値）
   * @throws AccessDeniedException 未認証、または所有者不一致の場合
   */
  public void assertOwner(Long resourceOwnerUserId) {
    AuthenticatedUser currentUser = currentUserProvider.requireCurrentUser();
    if (!currentUser.userId().equals(resourceOwnerUserId)) {
      throw new AccessDeniedException("Caller is not the owner of the target resource");
    }
  }
}
