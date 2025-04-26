package com.bd.blooddonerfinder.payload.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendMailRequest implements Serializable {
    private String mailTo;
    private String subject;
    private String body;
}
