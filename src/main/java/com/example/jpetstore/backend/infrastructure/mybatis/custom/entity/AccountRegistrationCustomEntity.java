package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

/**
 * ユーザー登録の {@code m_account} INSERT用パラメータエンティティ（#13）。
 *
 * <p>{@link #userId} は INSERT 実行後に {@code useGeneratedKeys}（AUTO_INCREMENT）で補完される。{@link #status}
 * はサーバ決定（"OK"固定・E7・PO決定＝内部項目）で呼び出し元（{@code MyBatisAccountRepository}）が設定する。 {@link
 * #createUserId}/{@link #updateUserId} は未認証guestによる登録のため常にNULL（E7・{@code AuditProgramInterceptor}
 * の自動補完対象外・呼び出し元が明示設定）。{@link #createProgram}/{@link #updateProgram} は同インターセプタが 自動補完する（{@code
 * RegistrationApplicationService} が {@code application.service} 配下にあるため）。
 *
 * <p>version列は書き込み対象に含めない（DB既定値0がそのまま使われる）。MyBatis Generator の生成対象外（{@code backend-conventions}
 * §9・カスタム手書きXMLマッパー）。
 */
public class AccountRegistrationCustomEntity {

  private Long userId;
  private String username;
  private String email;
  private String firstName;
  private String lastName;
  private String status;
  private String address1;
  private String address2;
  private String city;
  private String state;
  private String postalCode;
  private String country;
  private String phone;
  private Long createUserId;
  private String createProgram;
  private Long updateUserId;
  private String updateProgram;

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

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getAddress1() {
    return address1;
  }

  public void setAddress1(String address1) {
    this.address1 = address1;
  }

  public String getAddress2() {
    return address2;
  }

  public void setAddress2(String address2) {
    this.address2 = address2;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public void setPostalCode(String postalCode) {
    this.postalCode = postalCode;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public Long getCreateUserId() {
    return createUserId;
  }

  public void setCreateUserId(Long createUserId) {
    this.createUserId = createUserId;
  }

  public String getCreateProgram() {
    return createProgram;
  }

  public void setCreateProgram(String createProgram) {
    this.createProgram = createProgram;
  }

  public Long getUpdateUserId() {
    return updateUserId;
  }

  public void setUpdateUserId(Long updateUserId) {
    this.updateUserId = updateUserId;
  }

  public String getUpdateProgram() {
    return updateProgram;
  }

  public void setUpdateProgram(String updateProgram) {
    this.updateProgram = updateProgram;
  }
}
