package com.bd.blooddonorfinder.controller;

import com.bd.blooddonorfinder.payload.request.SendMailRequest;
import com.bd.blooddonorfinder.service.MailService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN')")
public class MailController {
    private final MailService mailService;
    private final String serverFromAddress;

    public MailController(MailService mailService,
                          @Value("${app.email.from}") String serverFromAddress) {
        this.mailService = mailService;
        this.serverFromAddress = serverFromAddress;
    }

    @PostMapping("/send")
    public ResponseEntity<String> sendMail(@Valid @RequestBody SendMailRequest sendMailRequest) {
        // Never trust a client-supplied From address: doing so allows arbitrary
        // sender spoofing / phishing through the platform's mail reputation.
        sendMailRequest.setMailFrom(serverFromAddress);
        mailService.sendMail(sendMailRequest);
        return ResponseEntity.ok().body("Mail sent");
    }
}
