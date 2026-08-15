package com.example.jpetstore.backend.presentation.rest;

import com.example.jpetstore.backend.domain.exception.OptimisticLockConflictException;
import com.example.jpetstore.backend.domain.exception.ResourceNotFoundException;
import com.example.jpetstore.backend.domain.security.OwnershipAuthorizationService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * secure-by-default 基盤の検証専用エンドポイント（AC2/AC3/AC4/AC8・#21 AC1/AC2/AC-neg1）。
 *
 * <p>本 Story で唯一追加が許可されている「保護テストエンドポイント」。ドメイン機能は持たない。 {@code @PreAuthorize} により
 * {@code @EnableMethodSecurity}（AC2）を実証し、{@code simulateError}
 * パラメータで正規化エラーハンドリング（AC4）・409統一マッピング（AC8）を実証する。
 *
 * <p>{@link #myResource} は #21 の本人スコープ（所有者一致）認可の実証。ADMIN ロールには依存せず、{@code
 * OwnershipAuthorizationService} が {@code CurrentUserProvider}（JWT検証済みprincipal）のみを identity
 * 源として判定する。
 */
@RestController
@RequestMapping("/api/secured")
public class SecuredPingController {

  private final OwnershipAuthorizationService ownershipAuthorizationService;

  public SecuredPingController(OwnershipAuthorizationService ownershipAuthorizationService) {
    this.ownershipAuthorizationService = ownershipAuthorizationService;
  }

  @GetMapping("/ping")
  @PreAuthorize("hasRole('ADMIN')")
  public Map<String, String> ping(@RequestParam(required = false) String simulateError) {
    if (simulateError != null) {
      throw switch (simulateError) {
        case "notFound" -> new ResourceNotFoundException("simulated not found");
        case "conflict" -> new OptimisticLockConflictException();
        case "illegalArgument" -> new IllegalArgumentException("simulated bad input");
        case "unexpected" ->
            new RuntimeException("simulated unexpected error at /internal/secret/path.java:1");
        default -> new IllegalArgumentException("unknown simulateError: " + simulateError);
      };
    }
    return Map.of("status", "ok");
  }

  /**
   * 本人スコープ（所有者一致）認可の実証エンドポイント（#21 AC1/AC2/AC-neg1）。
   *
   * <p>{@code resourceOwnerUserId} はパス変数＝「対象リソースの真の所有者 userId」を表す（実ドメインでは対象リソースIDからサーバー側で解決する値。本
   * Story では対象ドメインリソースが未実装のため、実証としてパス変数自体を解決済み所有者とみなす）。 認可判定はこの値と {@code CurrentUserProvider}
   * のみで行われ、クライアントが付与しうる他の request param（例: {@code ?userId=<他人>}）は一切参照しない （AC-neg1・SBD-1）。
   */
  @GetMapping("/my-resource/{resourceOwnerUserId}")
  public Map<String, String> myResource(@PathVariable Long resourceOwnerUserId) {
    ownershipAuthorizationService.assertOwner(resourceOwnerUserId);
    return Map.of("status", "ok");
  }
}
