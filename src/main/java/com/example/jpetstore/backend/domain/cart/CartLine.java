package com.example.jpetstore.backend.domain.cart;

/**
 * カート明細1行分の入力を表す検索条件VO相当のドメインモデル（#4）。ログインマージ（{@code
 * CartApplicationService#merge}）でクライアント（未ログイン・localStorage）側のカート内容を渡すために使う。
 *
 * <p>Presentation層のRequest DTO（Controller層に閉じる）から変換して Application層へ渡す（{@code backend-conventions}
 * §1「検索条件VOはPresentation/Application/Domain/Infrastructureいずれからも参照可」）。
 */
public record CartLine(String itemId, int quantity) {}
