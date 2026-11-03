package org.shakvilla.beatzmedia.engagement.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.engagement.application.port.out.EngagementRepository;
import org.shakvilla.beatzmedia.engagement.domain.ContentKind;
import org.shakvilla.beatzmedia.engagement.domain.EngagementEventType;
import org.shakvilla.beatzmedia.engagement.domain.ListeningCheckpoint;

@ApplicationScoped
public class JpaEngagementRepository implements EngagementRepository {

  private final EntityManager em;

  @Inject
  public JpaEngagementRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public PreferenceRow lockPreference(String accountId, Instant now) {
    em.createNativeQuery("""
        insert into listening_preference (account_id, history_enabled, updated_at)
        values (:accountId, true, :now)
        on conflict (account_id) do nothing
        """)
        .setParameter("accountId", accountId)
        .setParameter("now", now)
        .executeUpdate();
    Boolean enabled = (Boolean) em.createNativeQuery("""
        select history_enabled from listening_preference
        where account_id = :accountId for update
        """)
        .setParameter("accountId", accountId)
        .getSingleResult();
    return new PreferenceRow(enabled);
  }

  @Override
  public PreferenceRow preference(String accountId) {
    @SuppressWarnings("unchecked")
    List<Boolean> rows = em.createNativeQuery("""
        select history_enabled from listening_preference where account_id = :accountId
        """)
        .setParameter("accountId", accountId)
        .getResultList();
    return new PreferenceRow(rows.isEmpty() || rows.getFirst());
  }

  @Override
  public void savePreference(String accountId, boolean enabled, Instant now) {
    em.createNativeQuery("""
        insert into listening_preference (account_id, history_enabled, updated_at)
        values (:accountId, :enabled, :now)
        on conflict (account_id) do update
        set history_enabled = excluded.history_enabled, updated_at = excluded.updated_at
        """)
        .setParameter("accountId", accountId)
        .setParameter("enabled", enabled)
        .setParameter("now", now)
        .executeUpdate();
  }

  @Override
  public void createSession(String id, String accountId, Instant startedAt) {
    em.createNativeQuery("""
        insert into listening_session (id, account_id, started_at)
        values (:id, :accountId, :startedAt)
        """)
        .setParameter("id", id)
        .setParameter("accountId", accountId)
        .setParameter("startedAt", startedAt)
        .executeUpdate();
  }

  @Override
  public Optional<SessionRow> lockSession(String id, String accountId) {
    @SuppressWarnings("unchecked")
    List<Object[]> rows = em.createNativeQuery("""
        select id, account_id, max_sequence from listening_session
        where id = :id and account_id = :accountId for update
        """)
        .setParameter("id", id)
        .setParameter("accountId", accountId)
        .getResultList();
    if (rows.isEmpty()) return Optional.empty();
    Object[] row = rows.getFirst();
    return Optional.of(new SessionRow((String) row[0], (String) row[1], number(row[2])));
  }

  @Override
  public Optional<ListeningCheckpoint> checkpoint(String sessionId, long sequence) {
    @SuppressWarnings("unchecked")
    List<Object[]> rows = em.createNativeQuery("""
        select id, session_id, account_id, sequence, content_kind, content_id, event_type,
               position_seconds, duration_seconds, listened_delta_seconds, effective, received_at
        from listening_checkpoint where session_id = :sessionId and sequence = :sequence
        """)
        .setParameter("sessionId", sessionId)
        .setParameter("sequence", sequence)
        .getResultList();
    return rows.isEmpty() ? Optional.empty() : Optional.of(toCheckpoint(rows.getFirst()));
  }

  @Override
  public void insertCheckpoint(ListeningCheckpoint checkpoint) {
    em.createNativeQuery("""
        insert into listening_checkpoint (
          id, session_id, account_id, sequence, content_kind, content_id, event_type,
          position_seconds, duration_seconds, listened_delta_seconds, effective, received_at
        ) values (
          :id, :sessionId, :accountId, :sequence, :contentKind, :contentId, :eventType,
          :position, :duration, :listened, :effective, :receivedAt
        )
        """)
        .setParameter("id", checkpoint.id())
        .setParameter("sessionId", checkpoint.sessionId())
        .setParameter("accountId", checkpoint.accountId())
        .setParameter("sequence", checkpoint.sequence())
        .setParameter("contentKind", checkpoint.contentKind().wireValue())
        .setParameter("contentId", checkpoint.contentId())
        .setParameter("eventType", checkpoint.eventType().wireValue())
        .setParameter("position", checkpoint.positionSeconds())
        .setParameter("duration", checkpoint.durationSeconds())
        .setParameter("listened", checkpoint.listenedDeltaSeconds())
        .setParameter("effective", checkpoint.effective())
        .setParameter("receivedAt", checkpoint.receivedAt())
        .executeUpdate();
  }

  @Override
  public void advanceSessionAndProgress(ListeningCheckpoint checkpoint) {
    em.createNativeQuery("""
        update listening_session
        set max_sequence = :sequence, last_checkpoint_at = :receivedAt
        where id = :sessionId and account_id = :accountId and max_sequence < :sequence
        """)
        .setParameter("sequence", checkpoint.sequence())
        .setParameter("receivedAt", checkpoint.receivedAt())
        .setParameter("sessionId", checkpoint.sessionId())
        .setParameter("accountId", checkpoint.accountId())
        .executeUpdate();

    em.createNativeQuery("""
        insert into listening_progress (
          account_id, content_kind, content_id, position_seconds, duration_seconds,
          last_event_type, completed, last_session_id, last_sequence,
          last_checkpoint_id, last_activity_at
        ) values (
          :accountId, :contentKind, :contentId, :position, :duration,
          :eventType, :completed, :sessionId, :sequence, :checkpointId, :receivedAt
        )
        on conflict (account_id, content_kind, content_id) do update set
          position_seconds = excluded.position_seconds,
          duration_seconds = excluded.duration_seconds,
          last_event_type = excluded.last_event_type,
          completed = excluded.completed,
          last_session_id = excluded.last_session_id,
          last_sequence = excluded.last_sequence,
          last_checkpoint_id = excluded.last_checkpoint_id,
          last_activity_at = excluded.last_activity_at
        where (excluded.last_activity_at, excluded.last_checkpoint_id)
            > (listening_progress.last_activity_at, listening_progress.last_checkpoint_id)
        """)
        .setParameter("accountId", checkpoint.accountId())
        .setParameter("contentKind", checkpoint.contentKind().wireValue())
        .setParameter("contentId", checkpoint.contentId())
        .setParameter("position", Math.min(checkpoint.positionSeconds(), checkpoint.durationSeconds()))
        .setParameter("duration", checkpoint.durationSeconds())
        .setParameter("eventType", checkpoint.eventType().wireValue())
        .setParameter("completed", checkpoint.completed())
        .setParameter("sessionId", checkpoint.sessionId())
        .setParameter("sequence", checkpoint.sequence())
        .setParameter("checkpointId", checkpoint.id())
        .setParameter("receivedAt", checkpoint.receivedAt())
        .executeUpdate();
  }

  @Override
  public List<ProgressRow> progress(
      String accountId, ContentKind onlyKind, boolean incompleteOnly, Cursor cursor, int limit) {
    List<String> predicates = new ArrayList<>();
    predicates.add("account_id = :accountId");
    if (onlyKind != null) predicates.add("content_kind = :contentKind");
    if (incompleteOnly) {
      predicates.add("not completed");
      predicates.add("position_seconds >= 10");
    }
    if (cursor != null) {
      predicates.add("(last_activity_at, content_kind, content_id) < "
          + "(:cursorAt, :cursorKind, :cursorId)");
    }
    String sql = """
        select account_id, content_kind, content_id, position_seconds, duration_seconds,
               last_event_type, completed, last_activity_at
        from listening_progress where %s
        order by last_activity_at desc, content_kind desc, content_id desc limit :limit
        """.formatted(String.join(" and ", predicates));
    Query query = em.createNativeQuery(sql)
        .setParameter("accountId", accountId)
        .setParameter("limit", limit);
    if (onlyKind != null) query.setParameter("contentKind", onlyKind.wireValue());
    if (cursor != null) {
      query.setParameter("cursorAt", cursor.lastActivityAt());
      query.setParameter("cursorKind", cursor.contentKind().wireValue());
      query.setParameter("cursorId", cursor.contentId());
    }
    @SuppressWarnings("unchecked")
    List<Object[]> rows = query.getResultList();
    return rows.stream().map(JpaEngagementRepository::toProgress).toList();
  }

  @Override
  public void deletePersonalHistory(String accountId, Instant clearedAt) {
    em.createNativeQuery("delete from listening_progress where account_id = :accountId")
        .setParameter("accountId", accountId)
        .executeUpdate();
    em.createNativeQuery("delete from listening_session where account_id = :accountId")
        .setParameter("accountId", accountId)
        .executeUpdate();
    em.createNativeQuery("""
        update listening_preference
        set history_cleared_at = :clearedAt, updated_at = :clearedAt
        where account_id = :accountId
        """)
        .setParameter("clearedAt", clearedAt)
        .setParameter("accountId", accountId)
        .executeUpdate();
  }

  @Override
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public int projectPending(int batchSize, Instant projectedAt) {
    return em.createNativeQuery("""
        with claimed as materialized (
          select id, received_at, content_kind, content_id, event_type, listened_delta_seconds
          from listening_checkpoint
          where effective and aggregated_at is null
          order by received_at, id
          for update skip locked
          limit :batchSize
        ), rolled_up as (
          insert into engagement_daily_rollup (
            day, content_kind, content_id, starts, qualified_plays,
            completions, skips, repeats, listened_seconds
          )
          select
            (received_at at time zone 'UTC')::date,
            content_kind,
            content_id,
            count(*) filter (where event_type = 'start'),
            count(*) filter (where event_type = 'qualified-play'),
            count(*) filter (where event_type = 'completion'),
            count(*) filter (where event_type = 'skip'),
            count(*) filter (where event_type = 'repeat'),
            sum(listened_delta_seconds)
          from claimed
          group by (received_at at time zone 'UTC')::date, content_kind, content_id
          on conflict (day, content_kind, content_id) do update set
            starts = engagement_daily_rollup.starts + excluded.starts,
            qualified_plays = engagement_daily_rollup.qualified_plays + excluded.qualified_plays,
            completions = engagement_daily_rollup.completions + excluded.completions,
            skips = engagement_daily_rollup.skips + excluded.skips,
            repeats = engagement_daily_rollup.repeats + excluded.repeats,
            listened_seconds = engagement_daily_rollup.listened_seconds + excluded.listened_seconds
          returning 1
        )
        update listening_checkpoint
        set aggregated_at = :projectedAt
        where id in (select id from claimed)
        """)
        .setParameter("batchSize", batchSize)
        .setParameter("projectedAt", projectedAt)
        .executeUpdate();
  }

  private static ListeningCheckpoint toCheckpoint(Object[] row) {
    return new ListeningCheckpoint(
        (String) row[0],
        (String) row[1],
        (String) row[2],
        number(row[3]),
        ContentKind.fromWire((String) row[4]),
        (String) row[5],
        EngagementEventType.fromWire((String) row[6]),
        integer(row[7]),
        integer(row[8]),
        integer(row[9]),
        (Boolean) row[10],
        instant(row[11]));
  }

  private static ProgressRow toProgress(Object[] row) {
    return new ProgressRow(
        (String) row[0],
        ContentKind.fromWire((String) row[1]),
        (String) row[2],
        integer(row[3]),
        integer(row[4]),
        EngagementEventType.fromWire((String) row[5]),
        (Boolean) row[6],
        instant(row[7]));
  }

  private static int integer(Object value) {
    return ((Number) value).intValue();
  }

  private static long number(Object value) {
    return ((Number) value).longValue();
  }

  private static Instant instant(Object value) {
    if (value instanceof Instant instant) return instant;
    if (value instanceof OffsetDateTime offset) return offset.toInstant();
    return ((java.sql.Timestamp) value).toInstant().atOffset(ZoneOffset.UTC).toInstant();
  }
}
