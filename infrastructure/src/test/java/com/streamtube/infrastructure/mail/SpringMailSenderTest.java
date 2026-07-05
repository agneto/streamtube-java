package com.streamtube.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.infrastructure.transaction.AfterCommitExecutor;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

class SpringMailSenderTest {

  private JavaMailSender javaMailSender;
  private SpringMailSender sender;

  @BeforeEach
  void setUp() {
    javaMailSender = Mockito.mock(JavaMailSender.class);
    SpringTemplateEngine templateEngine = Mockito.mock(SpringTemplateEngine.class);
    when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html></html>");
    sender =
        new SpringMailSender(
            javaMailSender,
            templateEngine,
            new AfterCommitExecutor(),
            "no-reply@test.local",
            "http://localhost:8080");
  }

  @AfterEach
  void cleanUp() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void sendsOnlyAfterCommitInsideTransaction() {
    TransactionSynchronizationManager.initSynchronization();

    sender.sendConfirmationEmail("user@test.com", "token");
    verify(javaMailSender, never()).send(any(MimeMessage.class));

    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    verify(javaMailSender).send(any(MimeMessage.class));
  }

  @Test
  void doesNotSendOnRollback() {
    TransactionSynchronizationManager.initSynchronization();

    sender.sendPasswordResetEmail("user@test.com", "token");
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

    verify(javaMailSender, never()).send(any(MimeMessage.class));
  }

  @Test
  void smtpFailureIsLoggedNotPropagated() {
    doThrow(new MailSendException("smtp down")).when(javaMailSender).send(any(MimeMessage.class));

    assertThatCode(() -> sender.sendConfirmationEmail("user@test.com", "token"))
        .doesNotThrowAnyException();
  }
}
