package com.streamtube.application.port.out;

/** Output port for transactional email (implemented with Spring Mail + Thymeleaf in infra). */
public interface MailSender {

  void sendConfirmationEmail(String to, String rawToken);

  void sendPasswordResetEmail(String to, String rawToken);
}
