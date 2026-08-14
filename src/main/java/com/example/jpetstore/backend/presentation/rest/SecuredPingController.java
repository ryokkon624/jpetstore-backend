package com.example.jpetstore.backend.presentation.rest;

import com.example.jpetstore.backend.domain.exception.OptimisticLockConflictException;
import com.example.jpetstore.backend.domain.exception.ResourceNotFoundException;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * secure-by-default 基盤の検証専用エンドポイント（AC2/AC3/AC4/AC8）。
 *
 * <p>本 Story で唯一追加が許可されている「保護テストエンドポイント」。ドメイン機能は持たない。 {@code @PreAuthorize} により
 * {@code @EnableMethodSecurity}（AC2）を実証し、{@code simulateError}
 * パラメータで正規化エラーハンドリング（AC4）・409統一マッピング（AC8）を実証する。
 */
@RestController
@RequestMapping("/api/secured")
public class SecuredPingController {

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
}
