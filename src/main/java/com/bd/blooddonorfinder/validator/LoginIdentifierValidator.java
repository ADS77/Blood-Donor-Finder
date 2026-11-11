package com.bd.blooddonorfinder.validator;

import com.bd.blooddonorfinder.validator.annotations.ValidLoginIdentifier;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class LoginIdentifierValidator implements ConstraintValidator<ValidLoginIdentifier,String> {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    @Override
    public boolean isValid(String value, ConstraintValidatorContext validatorContext) {
        if(value == null || value.isBlank()){
            return true;
        }

        String identifier = value.trim();
        if(identifier.contains("@")){
            return EMAIL_PATTERN.matcher(identifier).matches();
        }
        return PHONE_PATTERN.matcher(identifier).matches();
    }
}
