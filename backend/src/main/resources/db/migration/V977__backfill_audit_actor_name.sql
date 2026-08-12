-- V977__backfill_audit_actor_name.sql
-- Fills in actor_name for audit rows written before the name was resolved at write time.
--
-- WHY. audit_entry.actor_name has existed since V942 and the application never populated it: all 48
-- call sites used the AuditEntry constructor overload that defaults it to null. The audit log
-- therefore rendered raw account UUIDs on a page whose subtitle promises "every privileged admin
-- action, with actor and time" — unreadable without a database session to resolve each id by hand.
--
-- Writes are fixed in JpaAuditWriter. This carries the existing history across; without it every row
-- logged before today stays anonymous forever, and the audit trail is least useful precisely where
-- it is oldest.
--
-- SCOPE. Only rows with no name, and only where the actor is a known account. Rows whose actor is
-- not an account — the scheduler, payment webhooks, a manual bootstrap — are left null on purpose:
-- inventing a name for them would be worse than leaving the id, which is at least true.
--
-- This reads account from a migration rather than through identity's port because a migration has no
-- application context; it is a one-off data fix, not an ongoing cross-module read. The runtime path
-- goes through ActorDirectory → identity's input port, as the dependency rule requires.

UPDATE audit_entry a
   SET actor_name = acc.name
  FROM account acc
 WHERE a.actor_id = acc.id
   AND a.actor_name IS NULL
   AND acc.name IS NOT NULL
   AND acc.name <> '';
