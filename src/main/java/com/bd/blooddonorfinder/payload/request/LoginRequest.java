package com.bd.blooddonorfinder.payload.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {
 @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone must be in E.164 format")
 String phone;

 @Email(message = "Must be a valid email address")
 @Size(max = 255, message = "Email must not exceed 255 characters")
 private String email;
}
