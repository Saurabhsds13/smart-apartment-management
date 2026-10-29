package com.societix.common.api;

import java.util.Objects;

/**
 * A minimal transactional email message (task 10.3; design.md "Email integration", A4).
 *
 * <p>Email is the only external communication channel in the MVP (A4). This immutable value carries
 * just what account-related messages (password reset, email verification) need: a single recipient,
 * a subject, and a plain-text body. It intentionally models no HTML, attachments, or bulk
 * recipients ? those are out of MVP scope.
 *
 * @param to      the recipient email address (non-blank)
 * @param subject the subject line (non-blank)
 * @param body    the plain-text body (non-blank)
 */
public record EmailMessage(String to, String subject, String body) {

    public EmailMessage {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(body, "body");
    }
}
