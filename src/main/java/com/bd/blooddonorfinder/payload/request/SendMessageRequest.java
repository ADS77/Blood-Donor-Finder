package com.bd.blooddonorfinder.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class SendMessageRequest implements Serializable {

    @NotBlank(message = "message body can't be blank")
    @Size(max = 2000, message = "message body can't exceed 2000 characters")
    private String body;
}
