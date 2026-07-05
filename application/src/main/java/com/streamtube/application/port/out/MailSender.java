package com.streamtube.application.port.out;

/**
 * Output port for transactional email (implemented with Spring Mail + Thymeleaf in infra).
 *
 * <p>Delivery is best-effort: implementations defer sending until the calling transaction commits
 * and never fail the business operation on delivery errors.
 */
public interface MailSender {

  void sendConfirmationEmail(String to, String rawToken);

  void sendPasswordResetEmail(String to, String rawToken);
}
