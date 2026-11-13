package com.bd.blooddonorfinder.payload.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Deserialized from the encrypted Kafka payload, so it must be constructible
 * by Jackson without relying on parameter-name introspection. A no-args
 * constructor plus setters is the most portable option: it works with any
 * {@link com.fasterxml.jackson.databind.ObjectMapper} configuration.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMailRequest implements Serializable {

    private String mailFrom;

    @NotEmpty(message = "At least one recipient is required")
    @Size(max = 50, message = "Too many recipients")
    private List<@Email(message = "Invalid recipient email") String> mailTo;

    @Size(max = 50, message = "Too many BCC recipients")
    private List<@Email(message = "Invalid BCC email") String> mailBcc;

    @Size(max = 50, message = "Too many CC recipients")
    private List<@Email(message = "Invalid CC email") String> mailCc;

    @NotBlank(message = "Subject is required")
    @Size(max = 200, message = "Subject must not exceed 200 characters")
    private String subject;

    @NotBlank(message = "Body is required")
    @Size(max = 100_000, message = "Body is too large")
    private String body;

    private boolean isHtmlContent;

    @Valid
    private List<MailAttachment> attachments;

    private Map<String, MailAttachment> inlineImages;

    public static SendMailRequest of(List<String> mailTo, String mailFrom, String subject, String body) {
        return SendMailRequest.builder()
                .isHtmlContent(true)
                .subject(subject)
                .mailTo(mailTo)
                .mailFrom(mailFrom)
                .body(body)
                .build();
    }
}
