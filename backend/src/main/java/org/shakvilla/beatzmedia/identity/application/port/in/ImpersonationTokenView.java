package org.shakvilla.beatzmedia.identity.application.port.in;

import java.time.Instant;
import java.util.Set;

/**
 * Result of issuing a scoped, time-boxed impersonation token (LLFR-ADMIN-02.5). {@code scopes} is
 * the exact role set embedded in the issued JWT — built the same way {@code LoginService} builds
 * its role set ({@code "fan"} + {@code "artist"} if applicable), but deliberately EXCLUDING any
 * admin role even if the target account happens to be an admin member (security default:
 * impersonation is for investigating regular users, never for an admin to gain another admin's
 * privileges — see admin ADD WU-ADM-2 as-built notes). Identity ADD §4.1.
 */
public record ImpersonationTokenView(String token, Instant expiresAt, Set<String> scopes) {}
