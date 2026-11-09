package com.bd.blooddonorfinder.payload.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponse implements Serializable {
    private int status;
    private LocalDateTime timestamp;
    private ErrorDetails errorDetails;
}
