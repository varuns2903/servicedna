package com.servicedna.common.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails (verification, password reset). SMTP is optional: with no
 * SMTP_HOST configured, sends fail fast and the message is logged instead, so registration and
 * password-reset flows keep working in local/dev environments that haven't set up mail delivery.
 */
@Service
public class MailService {

  private static final Logger log = LoggerFactory.getLogger(MailService.class);

  private final JavaMailSender mailSender;
  private final String fromAddress;

  public MailService(JavaMailSender mailSender, @Value("${mail.from}") String fromAddress) {
    this.mailSender = mailSender;
    this.fromAddress = fromAddress;
  }

  public void send(String to, String subject, String body) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromAddress);
    message.setTo(to);
    message.setSubject(subject);
    message.setText(body);

    try {
      mailSender.send(message);
    } catch (MailException e) {
      log.warn(
          "Could not send email to {} (SMTP not configured or unreachable): {}. Logging content instead:\nSubject: {}\n{}",
          to,
          e.getMessage(),
          subject,
          body);
    }
  }
}
