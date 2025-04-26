package com.bd.blooddonerfinder.service;

import com.bd.blooddonerfinder.payload.response.RestApiResponse;
import com.bd.blooddonerfinder.util.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MailServiceImpl implements MailService{
    private final JavaMailSender mailSender;

    public MailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    @Override
    public void sendMail(String mailTo, String subject, String body) {
        if(Utils.isValidEmail(mailTo)){
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo(mailTo);
            mailMessage.setSubject(subject);
            mailMessage.setText(body);

            try {
                log.debug("Sending mail to {}", mailTo);
                mailSender.send(mailMessage);
                log.debug("Mail sent to {}", mailTo);
            }
            catch (Exception e){
                log.error("Error while sending mail to {}, cause : {}", mailTo, e.getCause());
            }
        }
        else {
            log.error("Invalid email address: {}", mailTo);
        }
    }
}
