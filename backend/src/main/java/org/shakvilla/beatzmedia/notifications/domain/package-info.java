/**
 * Domain layer for the <strong>notifications</strong> bounded context.
 *
 * <p><b>Dependency rule (hexagonal — 00-system-architecture.md §4):</b>
 * the domain is framework-free pure Java. It must NOT depend on Jakarta,
 * Quarkus, Hibernate, REST, the AWS SDK, or any adapter. Dependencies point
 * inward only: {@code adapter -> application -> domain}. The application layer
 * may depend on this package; adapters may not reach past the application
 * ports. Cross-module access is forbidden — talk to another context only
 * through its {@code application.port.in} (ArchUnit enforces this).
 *
 * <p>No wall-clock or random ids in the core: inject the platform {@code Clock}
 * and {@code IdGenerator} ports instead of {@code Instant.now()} /
 * {@code UUID.randomUUID()} (testing-strategy.md §6).
 */
package org.shakvilla.beatzmedia.notifications.domain;
