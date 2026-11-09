package com.bd.blooddonorfinder.payload.request;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
public class SendMailRequest implements Serializable {
    private String mailFrom;
    List<String> mailTo;
    List<String>mailBcc;
    List<String>mailCc;
    private String subject;
    private String body;
    private boolean isHtmlContent;
    private List<MailAttachment> attachments;
    private Map<String, MailAttachment> inlineImages;

    public static SendMailRequest of (List<String> mailTo, String mailFrom, String subject, String body){
        return SendMailRequest.builder()
                .isHtmlContent(true)
                .subject(subject)
                .mailTo(mailTo)
                .mailFrom(mailFrom)
                .body(body)
                .build();
    }
}
