package org.shakvilla.beatzmedia.studio.application.port.in;

/**
 * A logged-in device/session entry. Category B (studio.md §16): no session-tracking infrastructure
 * exists anywhere in the codebase (JWTs are stateless) — {@link StudioSettingsView#sessions()} is
 * always {@code []}. Retained as a typed record only so the wire shape matches {@code
 * Frontend/src/lib/studio-data.ts}'s {@code SessionInfo} if this is ever backed for real.
 */
public record SessionView(String id, String device, String location, String lastActive, boolean current) {}
