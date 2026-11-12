package com.bd.blooddonorfinder.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender getMailSender(
            @Value("${spring.mail.host}") String host,
            @Value("${spring.mail.port}") int port,
            @Value("${spring.mail.username}") String username,
            @Value("${spring.mail.password}") String password) {

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);
        mailSender.setDefaultEncoding("UTF-8");

        Properties javaMailProperties = new Properties();
        javaMailProperties.put("mail.transport.protocol", "smtp");
        javaMailProperties.put("mail.smtp.auth", "true");
        // Require STARTTLS so credentials and message bodies are never sent in
        // clear text. Do not disable certificate validation with ssl.trust.
        javaMailProperties.put("mail.smtp.starttls.enable", "true");
        javaMailProperties.put("mail.smtp.starttls.required", "true");
        javaMailProperties.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        javaMailProperties.put("mail.smtp.connectiontimeout", "5000");
        javaMailProperties.put("mail.smtp.timeout", "5000");
        javaMailProperties.put("mail.smtp.writetimeout", "5000");
        // Never enable mail.debug in a deployed environment: it logs protocol
        // traffic that can include credentials and message contents.
        javaMailProperties.put("mail.debug", "false");

        mailSender.setJavaMailProperties(javaMailProperties);
        return mailSender;
    }
}
