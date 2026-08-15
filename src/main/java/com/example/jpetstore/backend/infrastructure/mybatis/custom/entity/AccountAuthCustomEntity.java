package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

/**
 * {@code m_account}×{@code m_signon} を username で結合した、認証照合専用のカスタムエンティティ（#18）。
 *
 * <p>MyBatis Generator の生成対象外（JOIN 結果を受け取るための手書き Entity。単純な1SQLのため配置・命名規約は {@code
 * backend-conventions} §9 のカスタム entity/mapper 規約に従う）。
 */
public class AccountAuthCustomEntity {

  private Long userId;
  private String username;
  private String passwordHash;

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }
}
