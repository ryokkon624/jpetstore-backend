package com.example.jpetstore.backend.presentation.rest.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

/**
 * {@link StrongPassword}の実装（#15/#17 Q1確定）。
 *
 * <p>長さは文字数（8〜72）に加え、UTF-8バイト長も72以下であることを検証する（マルチバイト文字混入時は
 * 文字数<=72でもバイト長が72を超えうるため。bcryptは内部的に72バイトで暗黙に切り詰めるため、切り詰め後の 実質的なパスワード短縮を防ぐ目的）。
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

  private static final int MIN_LENGTH = 8;
  private static final int MAX_LENGTH = 72;
  private static final int MIN_CHAR_CLASSES = 2;

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null || value.isEmpty()) {
      return false;
    }
    int length = value.length();
    if (length < MIN_LENGTH || length > MAX_LENGTH) {
      return false;
    }
    if (value.getBytes(StandardCharsets.UTF_8).length > MAX_LENGTH) {
      return false;
    }
    return countCharClasses(value) >= MIN_CHAR_CLASSES;
  }

  private int countCharClasses(String value) {
    boolean hasUpper = false;
    boolean hasLower = false;
    boolean hasDigit = false;
    boolean hasSymbol = false;

    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (Character.isUpperCase(c)) {
        hasUpper = true;
      } else if (Character.isLowerCase(c)) {
        hasLower = true;
      } else if (Character.isDigit(c)) {
        hasDigit = true;
      } else {
        hasSymbol = true;
      }
    }

    int classes = 0;
    if (hasUpper) {
      classes++;
    }
    if (hasLower) {
      classes++;
    }
    if (hasDigit) {
      classes++;
    }
    if (hasSymbol) {
      classes++;
    }
    return classes;
  }
}
