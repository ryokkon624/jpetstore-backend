package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

/**
 * アカウント編集の {@code m_account} UPDATE用パラメータエンティティ（#14 AC1〜AC3・version楽観ロック初実装）。
 *
 * <p>{@link #userId}/{@link #expectedVersion} がUPDATE文のWHERE句（{@code user_id=:id AND
 * version=:expectedVersion}）に使われる。{@link #updateUserId} は本人による自己編集のため常に{@link #userId}
 * と同値（呼び出し元の{@code MyBatisAccountRepository}が明示設定。{@code AuditProgramInterceptor}の自動補完 対象外）。{@link
 * #updateProgram} は同インターセプタが自動補完する。username/status/version(更新後の値)は サーバ側SQL（{@code version = version
 * + 1}）で決定するためフィールドを持たない（AC-neg2）。
 *
 * <p>MyBatis Generator の生成対象外（{@code backend-conventions} §9・カスタム手書きXMLマッパー）。
 */
public class AccountUpdateCustomEntity {

  private Long userId;
  private long expectedVersion;
  private String firstName;
  private String lastName;
  private String email;
  private String phone;
  private String address1;
  private String address2;
  private String city;
  private String state;
  private String postalCode;
  private String country;
  private Long updateUserId;
  private String updateProgram;

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public long getExpectedVersion() {
    return expectedVersion;
  }

  public void setExpectedVersion(long expectedVersion) {
    this.expectedVersion = expectedVersion;
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

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
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
