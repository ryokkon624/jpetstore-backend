package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

/**
 * 編集プリフィル用の {@code m_account}⋈{@code m_profile} JOIN読取専用エンティティ（#14 AC3・E3）。
 *
 * <p>MyBatis Generator の生成対象外（{@code backend-conventions} §9・カスタム手書きXMLマッパー）。
 * username/status/WHO列は保持しない（編集画面に出さない・内部項目）。
 */
public class AccountEditCustomEntity {

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
  private String languagePreference;
  private String favoriteCategoryId;
  private String colorSchemePreference;
  private long version;

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

  public String getLanguagePreference() {
    return languagePreference;
  }

  public void setLanguagePreference(String languagePreference) {
    this.languagePreference = languagePreference;
  }

  public String getFavoriteCategoryId() {
    return favoriteCategoryId;
  }

  public void setFavoriteCategoryId(String favoriteCategoryId) {
    this.favoriteCategoryId = favoriteCategoryId;
  }

  public String getColorSchemePreference() {
    return colorSchemePreference;
  }

  public void setColorSchemePreference(String colorSchemePreference) {
    this.colorSchemePreference = colorSchemePreference;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }
}
