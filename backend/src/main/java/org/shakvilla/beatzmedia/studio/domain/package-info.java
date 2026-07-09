/**
 * Domain layer for the <strong>studio</strong> (creator studio) bounded context.
 *
 * <p><b>Dependency rule (hexagonal — 00-system-architecture.md §4):</b> the domain is
 * framework-free pure Java. It must NOT depend on Jakarta, Quarkus, Hibernate, REST, or any
 * adapter. Dependencies point inward only: {@code adapter -> application -> domain}. The
 * application layer may depend on this package; adapters may not reach past the application
 * ports. Cross-module access is forbidden — talk to another context only through its {@code
 * application.port.in} (ArchUnit enforces this).
 *
 * <p>No wall-clock or random ids in the core: inject the platform {@code Clock} and {@code
 * IdGenerator} ports instead of {@code Instant.now()} / {@code UUID.randomUUID()}
 * (testing-strategy.md §6).
 *
 * <p>WU-STU-1 scope: only {@link org.shakvilla.beatzmedia.studio.domain.StudioProfile} (creator
 * profile get/save). WU-STU-2 adds {@link org.shakvilla.beatzmedia.studio.domain.PodcastShow} and
 * {@link org.shakvilla.beatzmedia.studio.domain.Episode} (create/manage, premium/early-access,
 * publish-now/schedule via {@link org.shakvilla.beatzmedia.studio.domain.EpisodeStatus}, INV-7).
 * Analytics/audience reads, settings, and payouts described in the module ADD land in later work
 * units (WU-STU-3/4).
 */
package org.shakvilla.beatzmedia.studio.domain;
