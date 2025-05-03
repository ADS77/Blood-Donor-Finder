package com.bd.blooddonerfinder.service;

import com.bd.blooddonerfinder.payload.request.MailAttachment;
import com.bd.blooddonerfinder.payload.request.SendMailRequest;
import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class MailServiceImpl implements MailService{
    private final JavaMailSender mailSender;

    public MailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }


    @Override
    public void sendMail(SendMailRequest mailRequest) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
            helper.setFrom(new InternetAddress(mailRequest.getMailFrom()));
            helper.setTo(mailRequest.getMailTo().toArray(new String[0]));

            if (mailRequest.getMailCc() != null && !mailRequest.getMailCc().isEmpty()) {
                helper.setCc(mailRequest.getMailCc().toArray(new String[0]));
            }
            if (mailRequest.getMailBcc() != null && !mailRequest.getMailBcc().isEmpty()) {
                helper.setBcc(mailRequest.getMailBcc().toArray(new String[0]));
            }

            helper.setSubject(mailRequest.getSubject());
            helper.setText(mailRequest.getBody(), mailRequest.isHtmlContent());

            if (mailRequest.getAttachments() != null && !mailRequest.getAttachments().isEmpty()) {
                for (MailAttachment attachment : mailRequest.getAttachments()) {
                    helper.addAttachment(attachment.getAttachmentName(), (DataSource) attachment.getDataSource());
                }
            }
            if (mailRequest.getInlineImages() != null && !mailRequest.getInlineImages().isEmpty()) {
                for (Map.Entry<String, MailAttachment> entry : mailRequest.getInlineImages().entrySet()) {
                    helper.addInline(entry.getKey(), (DataSource) entry.getValue().getDataSource());
                }
            }

            mailSender.send(mimeMessage);
            log.info("Mail sent successfully...............");
        } catch (MessagingException e) {
            log.error("Error sending email : {}",e.getCause());
            e.printStackTrace();
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
