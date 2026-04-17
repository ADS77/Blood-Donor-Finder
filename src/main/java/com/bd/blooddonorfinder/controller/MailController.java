package com.bd.blooddonorfinder.controller;

import com.bd.blooddonorfinder.payload.request.SendMailRequest;
import com.bd.blooddonorfinder.service.MailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail")
public class MailController {
    private final MailService mailService;

    public MailController(MailService mailService) {
        this.mailService = mailService;
    }

    @PostMapping("/send")
    public ResponseEntity<String> sendMail(@RequestBody SendMailRequest sendMailRequest){
        mailService.sendMail(sendMailRequest);
        return ResponseEntity.ok().body("Mail sent");
    }
}
