package com.bd.blooddonorfinder.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.ArrayList;
import java.util.List;

public class PasswordConstraintValidator implements ConstraintValidator<ValidPassword, String> {
    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 72;
    @Override
    public boolean isValid(String password, ConstraintValidatorContext constraintValidatorContext) {
        if (password == null) {
            // @NotBlank on the field handles null
            return true;
        }

        List<String> violations = new ArrayList<>();

        if (password.length() < MIN_LENGTH) {
            violations.add("Must be at least " + MIN_LENGTH + " characters");
        }
        if (password.length() > MAX_LENGTH) {
            violations.add("Must not exceed " + MAX_LENGTH + " characters");
        }
        if (!password.matches(".*[A-Z].*")) {
            violations.add("Must contain at least one uppercase letter");
        }
        if (!password.matches(".*[a-z].*")) {
            violations.add("Must contain at least one lowercase letter");
        }
        if (!password.matches(".*\\d.*")) {
            violations.add("Must contain at least one digit");
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?`~].*")) {
            violations.add("Must contain at least one special character");
        }
        if (password.matches(".*\\s.*")) {
            violations.add("Must not contain whitespace");
        }

        if (violations.isEmpty()) {
            return true;
        }

        constraintValidatorContext.disableDefaultConstraintViolation();
        for (String violation : violations) {
            constraintValidatorContext.buildConstraintViolationWithTemplate(violation)
                    .addConstraintViolation();
        }
        return false;
    }
}
