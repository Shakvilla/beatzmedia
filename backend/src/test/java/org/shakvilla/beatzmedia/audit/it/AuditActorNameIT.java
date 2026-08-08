package org.shakvilla.beatzmedia.audit.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies that an audit row is written with a readable actor name.
 *
 * <p><strong>Why.</strong> Every audit row the application wrote had a null {@code actor_name}, so
 * the audit log rendered raw UUIDs — {@code 019fe17e-…· SUBMIT_RELEASE · Release:019fe1af-…} — on a
 * page whose own subtitle promises "every privileged admin action, with actor and time". The
 * column, entity, domain field and read query all supported the name; only the 48 call sites never
 * passed one, each using the constructor overload that defaults it to null.
 *
 * <p>This drives the <em>real</em> {@link AuditWriter} against a real database rather than a copy of
 * its logic — the resolution lives in a private method, and asserting a re-implementation of it
 * would prove only that the test agrees with itself.
 */
@QuarkusTest
@Tag("integration")
class AuditActorNameIT {

  private static final Instant AT = Instant.parse("2026-08-08T10:00:00Z");

  @Inject
  AuditWriter auditWriter;

  @Inject
  EntityManager em;

  private String seedAccount(String name, long n) {
    String id = "audit-actor-" + n;
    em.createNativeQuery(
            "INSERT INTO account (id, name, email, is_artist, is_admin, status, created_at,"
                + " updated_at, verified) VALUES (:id, :name, :email, false, false, 'active',"
                + " now(), now(), false) ON CONFLICT (id) DO NOTHING")
        .setParameter("id", id)
        .setParameter("name", name)
        .setParameter("email", id + "@example.com")
        .executeUpdate();
    return id;
  }

  private String storedName(String entryId) {
    em.flush();
    em.clear();
    Object[] row = (Object[]) em.createNativeQuery(
            "SELECT actor_id, actor_name FROM audit_entry WHERE id = :id")
        .setParameter("id", entryId)
        .getSingleResult();
    return (String) row[1];
  }

  private AuditEntry entry(String id, String actor, String actorName) {
    return new AuditEntry(
        id, actor, actorName, "Suspended user", "Account", "target-1",
        AuditType.USER, "spam", AT);
  }

  @Test
  @Transactional
  void anAccountActorIsStoredWithItsDisplayName() {
    long n = System.nanoTime();
    String actorId = seedAccount("Abdul Shakur A Clement", n);
    String entryId = "audit-name-it-" + n;

    // Null name, exactly as all 48 production call sites pass it.
    auditWriter.append(entry(entryId, actorId, null));

    assertEquals("Abdul Shakur A Clement", storedName(entryId));
  }

  @Test
  @Transactional
  void anActorThatIsNotAnAccountIsLeftUnnamed() {
    // The scheduler, payment webhooks and manual bootstraps all audit without being accounts.
    // A null name is honest; an invented one would not be.
    String entryId = "audit-name-it-anon-" + System.nanoTime();

    auditWriter.append(entry(entryId, "catalog.go-live", null));

    assertNull(storedName(entryId));
  }

  @Test
  @Transactional
  void aNameSuppliedByTheCallerIsNotOverwritten() {
    // The @Audited interceptor takes a name from the JWT; a caller that knows better than the
    // directory must not have its value silently replaced.
    long n = System.nanoTime();
    String actorId = seedAccount("Directory Name", n);
    String entryId = "audit-name-it-explicit-" + n;

    auditWriter.append(entry(entryId, actorId, "Caller Name"));

    assertEquals("Caller Name", storedName(entryId));
  }
}
