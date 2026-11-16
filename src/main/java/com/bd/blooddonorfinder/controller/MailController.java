package com.bd.blooddonorfinder.controller;

import com.bd.blooddonorfinder.kafka.outbox.EmailOutboxRelayService;
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

/**
 * Direct mail sending — an arbitrary-email primitive. Locked to system
 * admins; all product email flows through the notification digest instead.
 */
@RestController
@RequestMapping("/api/mail")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN')")
public class MailController {
    private final MailService mailService;
    private final EmailOutboxRelayService outboxRelayService;
    private final String serverFromAddress;

    public MailController(MailService mailService,
                          EmailOutboxRelayService outboxRelayService,
                          @Value("${app.email.from}") String serverFromAddress) {
        this.mailService = mailService;
        this.outboxRelayService = outboxRelayService;
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

    /**
     * Requeues every FAILED email-outbox row for delivery (fresh attempt
     * budget). Support action after an SMTP/broker outage; the relay picks
     * the rows up on its next tick.
     */
    @PostMapping("/outbox/replay-failed")
    public ResponseEntity<String> replayFailedOutbox() {
        int requeued = outboxRelayService.requeueFailed();
        return ResponseEntity.ok().body("Requeued " + requeued + " failed email(s)");
    }
}
