package com.societix.common.api;

/**
 * Abstraction over the outbound email channel (task 10.3; design.md "Email integration", A4).
 *
 * <p>Email is the sole external channel in the MVP; SMS/WhatsApp are deferred. Account-related
 * flows (password reset, email verification) depend on this interface rather than on a concrete
 * mail client, so the provider is swappable via configuration and so tests can substitute a capture
 * implementation. Delivery is reported, never faked: an implementation returns
 * {@link DeliveryResult#NOT_CONFIGURED} when no transport is configured (Req 15.4) and
 * {@link DeliveryResult#FAILED} when a send is attempted but fails.
 *
 * <p>This abstraction lives in {@code common} (not {@code notification}) because identity-layer
 * flows need it, and routing it through the notification module would create a dependency cycle
 * (notification may depend on identity). The richer in-app NotificationService arrives with the
 * notification module in M4 and can delegate external email to this same sender.
 */
public interface EmailSender {

    /**
     * Attempts to deliver the given message, returning the outcome rather than throwing on a
     * transport failure.
     *
     * @param message the message to send
     * @return the delivery outcome (sent, failed, or not-configured)
     */
    DeliveryResult send(EmailMessage message);
}
