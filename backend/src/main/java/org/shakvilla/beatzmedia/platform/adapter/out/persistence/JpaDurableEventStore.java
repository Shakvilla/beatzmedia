package org.shakvilla.beatzmedia.platform.adapter.out.persistence;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.platform.application.port.out.DurableEventPublisher;
import org.shakvilla.beatzmedia.platform.application.port.out.DurableEventStore;
import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.DurableEventEnvelope;
import org.shakvilla.beatzmedia.platform.domain.DurableEventFilter;
import org.shakvilla.beatzmedia.platform.domain.DurableEventMessage;
import org.shakvilla.beatzmedia.platform.domain.DurableEventRecord;
import org.shakvilla.beatzmedia.platform.domain.DurableEventStatus;
import org.shakvilla.beatzmedia.platform.domain.DurableQueueHealth;
import org.shakvilla.beatzmedia.platform.domain.NotFoundException;
import org.shakvilla.beatzmedia.platform.domain.OutboxQueueSnapshot;
import org.shakvilla.beatzmedia.platform.domain.Page;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class JpaDurableEventStore implements DurableEventPublisher, DurableEventStore {

  private static final int LAST_ERROR_LIMIT = 2_000;
  private static final TypeReference<Map<String, String>> PAYLOAD_TYPE = new TypeReference<>() {};

  private final EntityManager em;
  private final ObjectMapper objectMapper;

  @Inject
  public JpaDurableEventStore(EntityManager em, ObjectMapper objectMapper) {
    this.em = em;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public void append(DurableEventMessage message) {
    em.createNativeQuery("""
        insert into platform_outbox_event (
          id, event_type, schema_version, aggregate_type, aggregate_id, payload,
          occurred_at, available_at, status, attempt_count
        ) values (
          :id, :eventType, :schemaVersion, :aggregateType, :aggregateId,
          cast(:payload as jsonb), :occurredAt, :occurredAt, 'pending', 0
        )
        """)
        .setParameter("id", message.id())
        .setParameter("eventType", message.eventType())
        .setParameter("schemaVersion", message.schemaVersion())
        .setParameter("aggregateType", message.aggregateType())
        .setParameter("aggregateId", message.aggregateId())
        .setParameter("payload", writePayload(message.payload()))
        .setParameter("occurredAt", message.occurredAt())
        .executeUpdate();
  }

  @Override
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public List<DurableEventEnvelope> claim(int batchSize, Instant now, Duration lease) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be positive");
    }
    String sql = """
        with candidates as (
          select id
          from platform_outbox_event
          where status in ('pending', 'processing')
            and available_at <= :now
            and (claimed_until is null or claimed_until < :now)
          order by occurred_at, id
          for update skip locked
          limit :batchSize
        )
        update platform_outbox_event event
        set status = 'processing',
            claimed_until = :claimedUntil,
            attempt_count = event.attempt_count + 1
        from candidates
        where event.id = candidates.id
        returning event.id, event.event_type, event.schema_version,
                  event.aggregate_type, event.aggregate_id, event.payload,
                  event.occurred_at, event.attempt_count
        """;

    @SuppressWarnings("unchecked")
    List<Object[]> claimed = em
        .createNativeQuery(sql)
        .setParameter("now", now)
        .setParameter("claimedUntil", now.plus(lease))
        .setParameter("batchSize", batchSize)
        .getResultList();
    return claimed.stream().map(this::toEnvelope).toList();
  }

  @Override
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public boolean wasHandled(String eventId, String consumerName) {
    Number count = (Number) em.createNativeQuery("""
        select count(*) from platform_inbox_receipt
        where event_id = :eventId and consumer_name = :consumerName
        """)
        .setParameter("eventId", eventId)
        .setParameter("consumerName", consumerName)
        .getSingleResult();
    return count.longValue() > 0;
  }

  @Override
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void markHandled(String eventId, String consumerName, Instant processedAt) {
    em.createNativeQuery("""
        insert into platform_inbox_receipt (consumer_name, event_id, processed_at)
        values (:consumerName, :eventId, :processedAt)
        on conflict (consumer_name, event_id) do nothing
        """)
        .setParameter("consumerName", consumerName)
        .setParameter("eventId", eventId)
        .setParameter("processedAt", processedAt)
        .executeUpdate();
  }

  @Override
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void markCompleted(String eventId, Instant completedAt) {
    em.createNativeQuery("""
        update platform_outbox_event
        set status = 'completed', completed_at = :completedAt,
            claimed_until = null, last_error = null
        where id = :eventId and completed_at is null
        """)
        .setParameter("completedAt", completedAt)
        .setParameter("eventId", eventId)
        .executeUpdate();
  }

  @Override
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void releaseForRetry(
      String eventId, Instant availableAt, String error, boolean terminalFailure) {
    em.createNativeQuery("""
        update platform_outbox_event
        set status = :status, available_at = :availableAt,
            claimed_until = null, last_error = :lastError
        where id = :eventId and completed_at is null
        """)
        .setParameter("status", terminalFailure ? "failed" : "pending")
        .setParameter("availableAt", availableAt)
        .setParameter("lastError", bounded(error))
        .setParameter("eventId", eventId)
        .executeUpdate();
  }

  @Override
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public OutboxQueueSnapshot snapshot(Instant now) {
    Object[] row = (Object[]) em.createNativeQuery("""
        select
          count(*) filter (where status = 'pending'),
          count(*) filter (where status = 'processing'),
          count(*) filter (where status = 'failed'),
          coalesce(extract(epoch from (
            :now - (min(occurred_at)
              filter (where status in ('pending', 'processing', 'failed')))
          )), 0)
        from platform_outbox_event
        where completed_at is null
        """)
        .setParameter("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
        .getSingleResult();
    return new OutboxQueueSnapshot(
        number(row[0]), number(row[1]), number(row[2]), number(row[3]));
  }

  @Override
  public Page<DurableEventRecord> search(DurableEventFilter filter) {
    List<String> predicates = new ArrayList<>();
    if (filter.status() != null) predicates.add("status = :status");
    if (filter.eventType() != null) predicates.add("event_type = :eventType");
    if (filter.aggregateId() != null) predicates.add("aggregate_id = :aggregateId");
    if (filter.occurredFrom() != null) predicates.add("occurred_at >= :occurredFrom");
    if (filter.occurredTo() != null) predicates.add("occurred_at <= :occurredTo");
    String where = predicates.isEmpty() ? "" : " where " + String.join(" and ", predicates);

    Query rows = em.createNativeQuery("""
        select id, event_type, schema_version, aggregate_type, aggregate_id,
               occurred_at, available_at, status, attempt_count, replay_count,
               claimed_until, completed_at, last_error, last_replayed_at, last_replayed_by
        from platform_outbox_event
        """ + where + " order by occurred_at desc, id desc limit :size offset :offset");
    bindFilter(rows, filter);
    rows.setParameter("size", filter.page().size());
    rows.setParameter("offset", filter.page().offset());

    Query count = em.createNativeQuery("select count(*) from platform_outbox_event" + where);
    bindFilter(count, filter);

    @SuppressWarnings("unchecked")
    List<Object[]> result = rows.getResultList();
    long total = ((Number) count.getSingleResult()).longValue();
    return Page.of(
        result.stream().map(JpaDurableEventStore::toRecord).toList(),
        filter.page().page(),
        filter.page().size(),
        total);
  }

  @Override
  public DurableEventRecord find(String eventId) {
    @SuppressWarnings("unchecked")
    List<Object[]> rows = em.createNativeQuery("""
        select id, event_type, schema_version, aggregate_type, aggregate_id,
               occurred_at, available_at, status, attempt_count, replay_count,
               claimed_until, completed_at, last_error, last_replayed_at, last_replayed_by
        from platform_outbox_event where id = :eventId
        """)
        .setParameter("eventId", eventId)
        .getResultList();
    if (rows.isEmpty()) throw new NotFoundException("Durable event not found: " + eventId);
    return toRecord(rows.getFirst());
  }

  @Override
  @Transactional
  public DurableEventRecord replay(String eventId, String actorId, Instant replayedAt) {
    int updated = em.createNativeQuery("""
        update platform_outbox_event
        set status = 'pending', available_at = :replayedAt, attempt_count = 0,
            claimed_until = null, completed_at = null, last_error = null,
            replay_count = replay_count + 1,
            last_replayed_at = :replayedAt, last_replayed_by = :actorId
        where id = :eventId and status = 'failed'
        """)
        .setParameter("replayedAt", replayedAt)
        .setParameter("actorId", actorId)
        .setParameter("eventId", eventId)
        .executeUpdate();
    if (updated != 1) {
      throw new ConflictException("Durable event is no longer eligible for replay");
    }
    return find(eventId);
  }

  @Override
  public DurableQueueHealth health(Instant now) {
    Object[] row = (Object[]) em.createNativeQuery("""
        select
          count(*) filter (where status = 'pending'),
          count(*) filter (where status = 'processing'),
          count(*) filter (where status = 'failed'),
          count(*) filter (where status = 'completed' and completed_at >= :completedSince),
          coalesce(extract(epoch from (
            :now - (min(occurred_at)
              filter (where status in ('pending', 'processing', 'failed')))
          )), 0)
        from platform_outbox_event
        """)
        .setParameter("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
        .setParameter("completedSince", now.minus(Duration.ofHours(1)))
        .getSingleResult();
    long failed = number(row[2]);
    long oldest = Math.max(0L, number(row[4]));
    String status = failed > 0 ? "degraded" : oldest > 300 ? "delayed" : "healthy";
    return new DurableQueueHealth(
        status, number(row[0]), number(row[1]), failed, number(row[3]), oldest);
  }

  private static void bindFilter(Query query, DurableEventFilter filter) {
    if (filter.status() != null) query.setParameter("status", filter.status().wireValue());
    if (filter.eventType() != null) query.setParameter("eventType", filter.eventType());
    if (filter.aggregateId() != null) query.setParameter("aggregateId", filter.aggregateId());
    if (filter.occurredFrom() != null) query.setParameter("occurredFrom", filter.occurredFrom());
    if (filter.occurredTo() != null) query.setParameter("occurredTo", filter.occurredTo());
  }

  private static DurableEventRecord toRecord(Object[] row) {
    return new DurableEventRecord(
        (String) row[0],
        (String) row[1],
        ((Number) row[2]).intValue(),
        (String) row[3],
        (String) row[4],
        instant(row[5]),
        instant(row[6]),
        DurableEventStatus.fromWireValue((String) row[7]),
        ((Number) row[8]).intValue(),
        ((Number) row[9]).intValue(),
        nullableInstant(row[10]),
        nullableInstant(row[11]),
        (String) row[12],
        nullableInstant(row[13]),
        (String) row[14]);
  }

  private DurableEventEnvelope toEnvelope(Object[] row) {
    return new DurableEventEnvelope(
        (String) row[0],
        (String) row[1],
        ((Number) row[2]).intValue(),
        (String) row[3],
        (String) row[4],
        readPayload(row[5]),
        instant(row[6]),
        ((Number) row[7]).intValue());
  }

  private String writePayload(Map<String, String> payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (Exception failure) {
      throw new IllegalArgumentException("Durable event payload is not serializable", failure);
    }
  }

  private Map<String, String> readPayload(Object payload) {
    try {
      return objectMapper.readValue(payload.toString(), PAYLOAD_TYPE);
    } catch (Exception failure) {
      throw new IllegalStateException("Stored durable event payload is invalid", failure);
    }
  }

  private static Instant instant(Object value) {
    if (value instanceof Instant instant) return instant;
    if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
    throw new IllegalStateException("Unsupported outbox timestamp type: " + value.getClass());
  }

  private static Instant nullableInstant(Object value) {
    return value == null ? null : instant(value);
  }

  private static long number(Object value) {
    return value == null ? 0L : ((Number) value).longValue();
  }

  private static String bounded(String value) {
    String safe = value == null || value.isBlank() ? "unknown handler failure" : value;
    return safe.length() <= LAST_ERROR_LIMIT ? safe : safe.substring(0, LAST_ERROR_LIMIT);
  }
}
