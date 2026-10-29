package com.societix.common.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.societix.common.api.DeliveryResult;
import com.societix.common.api.EmailMessage;
import com.societix.common.api.EmailSender;

/**
 * {@link EmailSender} backed by Spring's {@link JavaMailSender} (SMTP). Used when a mail transport
 * is configured (e.g. MailHog locally, a real SMTP provider in prod); see
 * {@link EmailSenderConfig} for the conditional wiring (task 10.3; A4).
 *
 * <p>Delivery is best-effort and reported: a {@link MailException} is caught and surfaced as
 * {@link DeliveryResult#FAILED} rather than propagated, so a transient mail outage does not abort
 * the surrounding operation (the caller decides what to do with a FAILED result). The recipient
 * address is logged but the body is never logged (it may contain a single-use credential).
 */
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailSender(JavaMailSender mailSender, String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public DeliveryResult send(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(fromAddress);
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        try {
            mailSender.send(mail);
            return DeliveryResult.SENT;
        } catch (MailException ex) {
            // Record the failure (recipient only, never the body/credential) and report FAILED.
            log.warn("Failed to send email to {}", message.to(), ex);
            return DeliveryResult.FAILED;
        }
    }
}
