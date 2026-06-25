package org.shakvilla.beatzmedia.identity.application.port.in;

/**
 * Result returned by the {@link RegisterFan} and {@link Login} use cases, carrying a signed JWT
 * and the minimal account view. Identity ADD §4.1.
 */
public record AuthResult(String token, AccountView account) {}
