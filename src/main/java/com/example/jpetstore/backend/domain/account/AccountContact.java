package com.example.jpetstore.backend.domain.account;

/**
 * 現在の認証プリンシパル自身の氏名/連絡先/住所を表す参照系ドメインモデル（#7・計画フェーズ確定①）。
 *
 * <p>チェックアウト・ウィザードの配送/請求先入力ステップのプリフィル専用。read-onlyに厳格限定し、username/status/version/WHO列・カード列は保持しない
 * （E4/F4.2 編集側・#8 送信/在庫を先取りしない）。
 */
public record AccountContact(
    String firstName,
    String lastName,
    String email,
    String phone,
    String address1,
    String address2,
    String city,
    String state,
    String postalCode,
    String country) {}
