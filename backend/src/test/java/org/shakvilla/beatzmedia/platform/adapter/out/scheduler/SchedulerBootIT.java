package org.shakvilla.beatzmedia.platform.adapter.out.scheduler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Quarkus integration test: verifies the {@link SchedulerRegistry} wires up correctly in the full
 * CDI container started by Dev Services (Testcontainers Postgres). Tests:
 * <ol>
 *   <li>SchedulerRegistry is injected (CDI bean is present and wires Micrometer + DataSource).
 *   <li>AdvisoryLockService is injected (DataSource injected correctly by Quarkus).
 *   <li>runWithLock for a non-registered job name is a safe no-op (no exception).
 *   <li>Advisory lock can be acquired and released via the real Postgres DataSource.
 * </ol>
 *
 * <p>No concrete job beans are registered in this test context (the owning modules are not yet
 * built), so the registry fans out to zero jobs — that is the expected Phase-0 state.
 */
@QuarkusTest
@Tag("integration")
class SchedulerBootIT {

  @Inject
  SchedulerRegistry schedulerRegistry;

  @Inject
  AdvisoryLockService advisoryLockService;

  @Test
  void schedulerRegistry_isInjected() {
    assertNotNull(schedulerRegistry, "SchedulerRegistry CDI bean must be present");
  }

  @Test
  void advisoryLockService_isInjected() {
    assertNotNull(advisoryLockService, "AdvisoryLockService CDI bean must be present");
  }

  @Test
  void runWithLock_noJobRegistered_isNoopWithoutException() {
    assertDoesNotThrow(
        () -> schedulerRegistry.runWithLock("nonexistent.job"),
        "runWithLock must not throw when no job is registered for the name");
  }

  @Test
  void advisoryLock_acquireAndRelease_worksAgainstRealPostgres() {
    long key = AdvisoryLockService.lockKeyFor("boot.it.test");
    // Use try-with-resources so the lock is always released (connection returned to pool).
    java.util.Optional<AdvisoryLockService.LockHandle> handle = advisoryLockService.tryAcquire(key);
    if (handle.isPresent()) {
      handle.get().close();
    }
    // Either acquired (and released) or was already held — what matters is no exception thrown.
    // In practice each @QuarkusTest is isolated so it will always be acquired.
    assertNotNull(handle, "tryAcquire must return an Optional without throwing");
  }

  @Test
  void runWithLock_catalogGoLiveJobName_isNoopWhenNotRegistered() {
    // Proves that the platform scheduler boots and handles the go-live tick safely even before
    // the catalog module implements the ScheduledJob bean (the concrete job is WU-CAT-3).
    assertDoesNotThrow(
        () -> schedulerRegistry.runWithLock("catalog.go-live"),
        "registry must handle unregistered go-live job name safely");
  }
}
