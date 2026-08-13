package com.example.jpetstore.backend.domain.exception;

/** 対象リソースが存在しないことを表す（AC4 → HTTP 404 に正規化マッピング）。 */
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String message) {
    super(message);
  }
}
