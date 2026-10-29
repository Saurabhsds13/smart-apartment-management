package com.societix.common.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import com.societix.common.api.EmailSender;

/**
 * Wires the active {@link EmailSender} (task 10.3; A4, Req 15.4).
 *
 * <p>When a {@link JavaMailSender} is present (Spring Boot auto-configures one whenever
 * {@code spring.mail.host} is set ? e.g. MailHog locally or a real SMTP provider in prod), the
 * {@link SmtpEmailSender} is used. Otherwise a {@link NoopEmailSender} is registered so the
 * application still starts and account flows degrade honestly to "not configured" (never a faked
 * delivery, Req 15.4). The {@code @ConditionalOnMissingBean} guard lets a test context supply its
 * own capturing {@link EmailSender}.
 */
@Configuration
public class EmailSenderConfig {

    @Bean
    @ConditionalOnBean(JavaMailSender.class)
    @ConditionalOnMissingBean(EmailSender.class)
    public EmailSender smtpEmailSender(
            JavaMailSender mailSender,
            @Value("${societix.mail.from:no-reply@societix.app}") String fromAddress) {
        return new SmtpEmailSender(mailSender, fromAddress);
    }

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    public EmailSender noopEmailSender() {
        return new NoopEmailSender();
    }
}
