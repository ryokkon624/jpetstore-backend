package com.example.jpetstore.backend.domain.enums;

public enum CardType implements CodeEnum {
  VISA("VISA"),
  MASTERCARD("MASTERCARD"),
  AMERICAN_EXPRESS("AMEX");

  private final String code;

  CardType(String code) {
    this.code = code;
  }

  @Override
  public String getCode() {
    return code;
  }

  public static CardType fromCode(String code) {
    for (CardType v : values()) {
      if (v.code.equals(code)) {
        return v;
      }
    }
    throw new IllegalArgumentException("Invalid CardType code: " + code);
  }
}
