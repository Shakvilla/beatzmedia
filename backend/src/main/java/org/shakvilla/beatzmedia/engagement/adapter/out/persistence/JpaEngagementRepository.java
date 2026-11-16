package org.shakvilla.beatzmedia.engagement.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.engagement.application.port.in.GetDiscoveryEngagementMetrics;
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

  @Override
  public List<GetDiscoveryEngagementMetrics.ContentMetrics> discoveryMetrics(
      List<GetDiscoveryEngagementMetrics.ContentRef> content, Instant asOf) {
    Set<String> keys = content.stream()
        .map(ref -> ref.kind() + ":" + ref.id())
        .collect(Collectors.toSet());
    List<String> ids = content.stream().map(GetDiscoveryEngagementMetrics.ContentRef::id)
        .distinct().toList();
    @SuppressWarnings("unchecked")
    List<Object[]> rows = em.createNativeQuery("""
        with per_listener_day as (
          select content_kind, content_id, account_id,
                 (received_at at time zone 'UTC')::date as day,
                 count(*) filter (where event_type = 'qualified-play') as plays28,
                 count(*) filter (where event_type = 'qualified-play'
                   and received_at >= :recentStart) as plays24,
                 count(*) filter (where event_type = 'qualified-play'
                   and received_at >= :baselineStart and received_at < :recentStart) as playsBaseline,
                 count(*) filter (where event_type = 'completion') as completions
          from listening_checkpoint
          where effective and received_at >= :windowStart and received_at < :asOf
            and content_id in (:ids)
            and event_type in ('qualified-play', 'completion')
          group by content_kind, content_id, account_id,
                   (received_at at time zone 'UTC')::date
        )
        select content_kind, content_id,
               sum(least(plays28, 5)),
               sum(least(plays24, 5)),
               sum(least(playsBaseline, 5)),
               count(distinct account_id) filter (where plays28 > 0),
               count(distinct account_id) filter (where plays24 > 0),
               sum(least(completions, 5))
        from per_listener_day
        group by content_kind, content_id
        """)
        .setParameter("windowStart", asOf.minus(java.time.Duration.ofDays(28)))
        .setParameter("baselineStart", asOf.minus(java.time.Duration.ofDays(8)))
        .setParameter("recentStart", asOf.minus(java.time.Duration.ofDays(1)))
        .setParameter("asOf", asOf)
        .setParameter("ids", ids)
        .getResultList();
    return rows.stream()
        .filter(row -> keys.contains(row[0] + ":" + row[1]))
        .map(row -> new GetDiscoveryEngagementMetrics.ContentMetrics(
            (String) row[0], (String) row[1], number(row[2]), number(row[3]),
            number(row[4]), number(row[5]), number(row[6]), number(row[7])))
        .toList();
  }

  @Override
  public List<GetDiscoveryEngagementMetrics.ContentRef> discoveryCandidates(
      Instant asOf, int perWindowLimit) {
    var today = asOf.atZone(ZoneOffset.UTC).toLocalDate();
    var selected = new LinkedHashSet<GetDiscoveryEngagementMetrics.ContentRef>();
    selected.addAll(candidateWindow(today.minusDays(27), today, perWindowLimit));
    selected.addAll(candidateWindow(today.minusDays(1), today, perWindowLimit));
    return List.copyOf(selected);
  }

  private List<GetDiscoveryEngagementMetrics.ContentRef> candidateWindow(
      java.time.LocalDate from, java.time.LocalDate through, int limit) {
    @SuppressWarnings("unchecked")
    List<Object[]> rows = em.createNativeQuery("""
        select content_kind, content_id
        from engagement_daily_rollup
        where day >= :from and day <= :through and qualified_plays > 0
        group by content_kind, content_id
        order by sum(qualified_plays) desc, content_kind, content_id
        limit :limit
        """)
        .setParameter("from", from)
        .setParameter("through", through)
        .setParameter("limit", limit)
        .getResultList();
    return rows.stream()
        .map(row -> new GetDiscoveryEngagementMetrics.ContentRef(
            (String) row[0], (String) row[1]))
        .toList();
  }

  @Override
  public GetDiscoveryEngagementMetrics.GroupMetrics discoveryGroupMetrics(
      List<GetDiscoveryEngagementMetrics.ContentRef> members, Instant asOf) {
    String values = IntStream.range(0, members.size())
        .mapToObj(index -> "(:kind" + index + ", :id" + index + ")")
        .collect(Collectors.joining(", "));
    Query query = em.createNativeQuery("""
        with requested(kind, id) as (values %s),
        per_listener_day as (
          select c.account_id, (c.received_at at time zone 'UTC')::date as day,
                 count(*) filter (where c.event_type = 'qualified-play') as plays28,
                 count(*) filter (where c.event_type = 'qualified-play'
                   and c.received_at >= :recentStart) as plays24,
                 count(*) filter (where c.event_type = 'qualified-play'
                   and c.received_at >= :baselineStart
                   and c.received_at < :recentStart) as playsBaseline,
                 count(*) filter (where c.event_type = 'completion') as completions
          from listening_checkpoint c
          join requested r on r.kind = c.content_kind and r.id = c.content_id
          where c.effective and c.received_at >= :windowStart and c.received_at < :asOf
            and c.event_type in ('qualified-play', 'completion')
          group by c.account_id, (c.received_at at time zone 'UTC')::date
        )
        select coalesce(sum(least(plays28, 5)), 0),
               coalesce(sum(least(plays24, 5)), 0),
               coalesce(sum(least(playsBaseline, 5)), 0),
               count(distinct account_id) filter (where plays28 > 0),
               count(distinct account_id) filter (where plays24 > 0),
               coalesce(sum(least(completions, 5)), 0)
        from per_listener_day
        """.formatted(values))
        .setParameter("windowStart", asOf.minus(java.time.Duration.ofDays(28)))
        .setParameter("baselineStart", asOf.minus(java.time.Duration.ofDays(8)))
        .setParameter("recentStart", asOf.minus(java.time.Duration.ofDays(1)))
        .setParameter("asOf", asOf);
    for (int index = 0; index < members.size(); index++) {
      query.setParameter("kind" + index, members.get(index).kind());
      query.setParameter("id" + index, members.get(index).id());
    }
    Object[] row = (Object[]) query.getSingleResult();
    return new GetDiscoveryEngagementMetrics.GroupMetrics(
        number(row[0]), number(row[1]), number(row[2]), number(row[3]),
        number(row[4]), number(row[5]));
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
