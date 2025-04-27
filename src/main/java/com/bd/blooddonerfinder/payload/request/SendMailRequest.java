package com.bd.blooddonerfinder.payload.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
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

    public SendMailRequest(String emailTo, String mailFrom, String subject, String body) {
        this.mailFrom = mailFrom;
        this.mailTo = Collections.singletonList(emailTo);
        this.subject = subject;
        this.body = body;
    }
}
