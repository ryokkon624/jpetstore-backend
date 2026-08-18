package com.example.jpetstore.backend.presentation.rest.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PW強度の共有Bean Validation制約（#15/#17 Q1確定・計画フェーズ確定）。
 *
 * <p>8〜72文字・UTF-8で72バイト以下（bcryptの72バイト上限・暗黙切り詰め防止）・英大文字/英小文字/数字/記号の 4種中2種以上を必須とする。{@code
 * RegistrationController.RegisterRequest#password}（#17）と{@code
 * AccountController.PasswordChangeRequest#newPassword}（#15）の両方へ付与し、PW強度ロジックを1本化する （別実装で仕様分岐させない）。
 *
 * @see StrongPasswordValidator
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrongPasswordValidator.class)
public @interface StrongPassword {

  String message() default
      "Password must be 8-72 characters and include at least 2 of: uppercase letters, "
          + "lowercase letters, digits, symbols.";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
