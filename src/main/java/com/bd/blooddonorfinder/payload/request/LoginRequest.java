package com.bd.blooddonorfinder.payload.request;

import com.bd.blooddonorfinder.validator.annotations.ValidLoginIdentifier;
import com.bd.blooddonorfinder.validator.annotations.ValidPassword;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
public class LoginRequest {
 @NotBlank(message = "Email or phone number is required")
 @ValidLoginIdentifier
 private String identifier;

 @NotBlank(message = "Password is required")
 @ValidPassword
 private String password;
}
