package org.shakvilla.beatzmedia.studio.application.port.in;

/**
 * A third-party connected app entry. Category B (studio.md §16): no OAuth/third-party integration
 * infrastructure exists anywhere in the codebase — {@link StudioSettingsView#connectedApps()} is
 * always {@code []}. Retained as a typed record only so the wire shape matches {@code
 * Frontend/src/lib/studio-data.ts}'s {@code ConnectedApp} if this is ever backed for real.
 */
public record ConnectedAppView(String id, String name, String description, boolean connected) {}
