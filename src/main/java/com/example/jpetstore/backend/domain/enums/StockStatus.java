package com.example.jpetstore.backend.domain.enums;

public enum StockStatus implements CodeEnum {
  IN_STOCK("IN_STOCK"),
  LOW_STOCK("LOW_STOCK"),
  OUT_OF_STOCK("OUT_STOCK");

  private final String code;

  StockStatus(String code) {
    this.code = code;
  }

  @Override
  public String getCode() {
    return code;
  }

  public static StockStatus fromCode(String code) {
    for (StockStatus v : values()) {
      if (v.code.equals(code)) {
        return v;
      }
    }
    throw new IllegalArgumentException("Invalid StockStatus code: " + code);
  }
}
