package com.streamtube.infrastructure.mail;

import com.streamtube.application.port.out.MailSender;
import com.streamtube.infrastructure.transaction.AfterCommitExecutor;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Sends transactional HTML emails rendered from Thymeleaf templates (Mailpit in dev).
 *
 * <p>Delivery is deferred until the surrounding transaction commits and is best-effort: an SMTP
 * failure is logged, never propagated, so a slow or down mail server cannot hold the transaction
 * nor roll back the business operation (the user can always request a resend).
 */
@Component
public class SpringMailSender implements MailSender {

  private static final Logger log = LoggerFactory.getLogger(SpringMailSender.class);

  private final JavaMailSender mailSender;
  private final SpringTemplateEngine templateEngine;
  private final AfterCommitExecutor afterCommit;
  private final String fromAddress;
  private final String baseUrl;

  public SpringMailSender(
      JavaMailSender mailSender,
      SpringTemplateEngine templateEngine,
      AfterCommitExecutor afterCommit,
      @Value("${app.mail.from:no-reply@streamtube.local}") String fromAddress,
      @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
    this.mailSender = mailSender;
    this.templateEngine = templateEngine;
    this.afterCommit = afterCommit;
    this.fromAddress = fromAddress;
    this.baseUrl = baseUrl;
  }

  @Override
  public void sendConfirmationEmail(String to, String rawToken) {
    Context ctx = new Context();
    ctx.setVariable("link", baseUrl + "/auth/confirm-email?token=" + rawToken);
    send(to, "Confirm your StreamTube account", "email/confirmation", ctx);
  }

  @Override
  public void sendPasswordResetEmail(String to, String rawToken) {
    Context ctx = new Context();
    ctx.setVariable("link", baseUrl + "/auth/reset-password?token=" + rawToken);
    send(to, "Reset your StreamTube password", "email/password-reset", ctx);
  }

  private void send(String to, String subject, String template, Context context) {
    afterCommit.run(
        () -> {
          try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(templateEngine.process(template, context), true);
            mailSender.send(message);
          } catch (Exception e) {
            log.error("Failed to send email '{}' to {}", subject, to, e);
          }
        });
  }
}
