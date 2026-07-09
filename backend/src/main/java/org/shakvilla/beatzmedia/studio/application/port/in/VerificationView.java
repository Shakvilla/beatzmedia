package org.shakvilla.beatzmedia.studio.application.port.in;

/**
 * Verification badge status. Category B (studio.md §16): {@code artist} is real (this endpoint is
 * {@code @RolesAllowed("artist")}-gated, so it is always {@code true} for a caller who reaches this
 * code). {@code identity}/{@code payout}/{@code rights} have no backing KYC/rights-management
 * subsystem reachable from {@code studio} and are always {@code false} — no new cross-module port
 * was added to source them (out of scope for this WU).
 */
public record VerificationView(boolean artist, boolean identity, boolean payout, boolean rights) {}
