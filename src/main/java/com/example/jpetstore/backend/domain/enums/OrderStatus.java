package com.example.jpetstore.backend.domain.enums;

public enum OrderStatus implements CodeEnum {
  NEW("NEW"),
  PAID("PAID"),
  SHIPPED("SHIPPED");

  private final String code;

  OrderStatus(String code) {
    this.code = code;
  }

  @Override
  public String getCode() {
    return code;
  }

  public static OrderStatus fromCode(String code) {
    for (OrderStatus v : values()) {
      if (v.code.equals(code)) {
        return v;
      }
    }
    throw new IllegalArgumentException("Invalid OrderStatus code: " + code);
  }
}
