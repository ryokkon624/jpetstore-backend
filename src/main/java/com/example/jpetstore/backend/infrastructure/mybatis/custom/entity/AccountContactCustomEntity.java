package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

/**
 * プリフィル用の氏名/連絡先/住所参照専用カスタムエンティティ（#7・計画フェーズ確定①）。
 *
 * <p>MyBatis Generator の生成対象外（{@code m_account} を単純 SELECT するための手書き Entity。配置・命名規約は {@code
 * backend-conventions} §9 に従う）。username/status/version/WHO列・カード列は保持しない（read-onlyに厳格限定・E4/F4.2
 * 編集側・#8 送信/在庫を先取りしない）。
 */
public class AccountContactCustomEntity {

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
}
