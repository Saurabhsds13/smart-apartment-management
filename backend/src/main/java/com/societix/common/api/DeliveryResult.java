package com.societix.common.api;

/**
 * Outcome of attempting to deliver an {@link EmailMessage} through a channel (task 10.3; A4).
 *
 * <p>Delivery is best-effort and its result is recorded rather than thrown, so callers can decide
 * whether a transient channel failure should fail the surrounding operation. The platform never
 * claims delivery through a channel that is not configured and implemented (Req 15.4): an
 * unconfigured sender returns {@link #NOT_CONFIGURED}.
 */
public enum DeliveryResult {
    /** The message was handed to the transport successfully. */
    SENT,
    /** Delivery was attempted but failed (e.g. SMTP error); recorded, not claimed as delivered. */
    FAILED,
    /** No external channel is configured, so nothing was sent and nothing is claimed (Req 15.4). */
    NOT_CONFIGURED
}
