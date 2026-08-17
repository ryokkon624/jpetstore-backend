package com.example.jpetstore.backend.infrastructure.mybatis.custom.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code t_order}（注文ヘッダ）の書き込み用パラメータエンティティ（#8）。
 *
 * <p>{@link #orderId} は INSERT 実行後に {@code useGeneratedKeys}（AUTO_INCREMENT・ID-23）で補完される。 {@link
 * #createUserId}/{@link #updateUserId} は呼び出し元（{@code OrderApplicationService}）が {@code
 * CurrentUserProvider} から明示設定する（{@code AuditProgramInterceptor} の自動補完対象外。カートの {@code
 * CartHeaderCustomEntity} と同じ規約）。{@link #createProgram}/{@link #updateProgram} は同インターセプタが自動補完する
 * （{@code OrderApplicationService} が {@code application.service} 配下にあるため）。
 */
public class OrderHeaderWriteCustomEntity {

  private Long orderId;
  private Long userId;
  private LocalDate orderDate;
  private String shipAddress1;
  private String shipAddress2;
  private String shipCity;
  private String shipState;
  private String shipPostalCode;
  private String shipCountry;
  private String billAddress1;
  private String billAddress2;
  private String billCity;
  private String billState;
  private String billPostalCode;
  private String billCountry;
  private BigDecimal totalPrice;
  private String billToFirstName;
  private String billToLastName;
  private String shipToFirstName;
  private String shipToLastName;
  private String statusCode;
  private Long createUserId;
  private String createProgram;
  private Long updateUserId;
  private String updateProgram;

  public Long getOrderId() {
    return orderId;
  }

  public void setOrderId(Long orderId) {
    this.orderId = orderId;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public LocalDate getOrderDate() {
    return orderDate;
  }

  public void setOrderDate(LocalDate orderDate) {
    this.orderDate = orderDate;
  }

  public String getShipAddress1() {
    return shipAddress1;
  }

  public void setShipAddress1(String shipAddress1) {
    this.shipAddress1 = shipAddress1;
  }

  public String getShipAddress2() {
    return shipAddress2;
  }

  public void setShipAddress2(String shipAddress2) {
    this.shipAddress2 = shipAddress2;
  }

  public String getShipCity() {
    return shipCity;
  }

  public void setShipCity(String shipCity) {
    this.shipCity = shipCity;
  }

  public String getShipState() {
    return shipState;
  }

  public void setShipState(String shipState) {
    this.shipState = shipState;
  }

  public String getShipPostalCode() {
    return shipPostalCode;
  }

  public void setShipPostalCode(String shipPostalCode) {
    this.shipPostalCode = shipPostalCode;
  }

  public String getShipCountry() {
    return shipCountry;
  }

  public void setShipCountry(String shipCountry) {
    this.shipCountry = shipCountry;
  }

  public String getBillAddress1() {
    return billAddress1;
  }

  public void setBillAddress1(String billAddress1) {
    this.billAddress1 = billAddress1;
  }

  public String getBillAddress2() {
    return billAddress2;
  }

  public void setBillAddress2(String billAddress2) {
    this.billAddress2 = billAddress2;
  }

  public String getBillCity() {
    return billCity;
  }

  public void setBillCity(String billCity) {
    this.billCity = billCity;
  }

  public String getBillState() {
    return billState;
  }

  public void setBillState(String billState) {
    this.billState = billState;
  }

  public String getBillPostalCode() {
    return billPostalCode;
  }

  public void setBillPostalCode(String billPostalCode) {
    this.billPostalCode = billPostalCode;
  }

  public String getBillCountry() {
    return billCountry;
  }

  public void setBillCountry(String billCountry) {
    this.billCountry = billCountry;
  }

  public BigDecimal getTotalPrice() {
    return totalPrice;
  }

  public void setTotalPrice(BigDecimal totalPrice) {
    this.totalPrice = totalPrice;
  }

  public String getBillToFirstName() {
    return billToFirstName;
  }

  public void setBillToFirstName(String billToFirstName) {
    this.billToFirstName = billToFirstName;
  }

  public String getBillToLastName() {
    return billToLastName;
  }

  public void setBillToLastName(String billToLastName) {
    this.billToLastName = billToLastName;
  }

  public String getShipToFirstName() {
    return shipToFirstName;
  }

  public void setShipToFirstName(String shipToFirstName) {
    this.shipToFirstName = shipToFirstName;
  }

  public String getShipToLastName() {
    return shipToLastName;
  }

  public void setShipToLastName(String shipToLastName) {
    this.shipToLastName = shipToLastName;
  }

  public String getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(String statusCode) {
    this.statusCode = statusCode;
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
