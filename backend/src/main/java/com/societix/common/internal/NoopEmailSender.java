package com.societix.common.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.societix.common.api.DeliveryResult;
import com.societix.common.api.EmailMessage;
import com.societix.common.api.EmailSender;

/**
 * Fallback {@link EmailSender} used when NO mail transport is configured (task 10.3; A4, Req 15.4).
 *
 * <p>It sends nothing and returns {@link DeliveryResult#NOT_CONFIGURED}, so the platform never
 * claims a message was delivered through a channel that is not actually configured (Req 15.4). It
 * logs that an email WOULD have been sent (recipient + subject only, never the body, which may hold
 * a single-use credential) so developers can see the flow locally without a mail server.
 */
public class NoopEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(NoopEmailSender.class);

    @Override
    public DeliveryResult send(EmailMessage message) {
        log.info("Email channel not configured; would have sent to {} with subject \"{}\"",
                message.to(), message.subject());
        return DeliveryResult.NOT_CONFIGURED;
    }
}
