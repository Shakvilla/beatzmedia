package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.shakvilla.beatzmedia.admin.application.port.out.SupportTicketRepository;
import org.shakvilla.beatzmedia.admin.domain.MessageFrom;
import org.shakvilla.beatzmedia.admin.domain.SupportMessage;
import org.shakvilla.beatzmedia.admin.domain.SupportTicket;
import org.shakvilla.beatzmedia.admin.domain.TicketPriority;
import org.shakvilla.beatzmedia.admin.domain.TicketStatus;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * JPA implementation of {@link SupportTicketRepository} (admin ADD §5.2 / §7, WU-ADM-7). Reads/
 * writes only this module's V950 tables ({@code support_ticket}, {@code support_message}); no
 * cross-module joins. Transaction boundary = the calling application service.
 */
@ApplicationScoped
public class JpaSupportTicketRepository implements SupportTicketRepository {

  private final EntityManager em;

  @Inject
  public JpaSupportTicketRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public Page<SupportTicket> list(TicketStatus status, String q, PageRequest page) {
    StringBuilder jpql = new StringBuilder("SELECT t FROM SupportTicketEntity t WHERE 1=1");
    StringBuilder countJpql = new StringBuilder("SELECT COUNT(t) FROM SupportTicketEntity t WHERE 1=1");
    if (status != null) {
      jpql.append(" AND t.status = :status");
      countJpql.append(" AND t.status = :status");
    }
    if (q != null && !q.isBlank()) {
      jpql.append(" AND (LOWER(t.subject) LIKE :q OR LOWER(t.requesterRef) LIKE :q)");
      countJpql.append(" AND (LOWER(t.subject) LIKE :q OR LOWER(t.requesterRef) LIKE :q)");
    }
    jpql.append(" ORDER BY t.createdAt DESC");

    TypedQuery<SupportTicketEntity> query =
        em.createQuery(jpql.toString(), SupportTicketEntity.class);
    TypedQuery<Long> countQuery = em.createQuery(countJpql.toString(), Long.class);
    if (status != null) {
      query.setParameter("status", status.wireValue());
      countQuery.setParameter("status", status.wireValue());
    }
    if (q != null && !q.isBlank()) {
      String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
      query.setParameter("q", like);
      countQuery.setParameter("q", like);
    }

    List<SupportTicket> items =
        query
            .setFirstResult(page.offset())
            .setMaxResults(page.size())
            .getResultList()
            .stream()
            .map(JpaSupportTicketRepository::toDomain)
            .toList();
    long total = countQuery.getSingleResult();

    return new Page<>(items, page.page(), page.size(), total);
  }

  @Override
  public Optional<SupportTicket> findById(String ticketId) {
    SupportTicketEntity entity = em.find(SupportTicketEntity.class, ticketId);
    return Optional.ofNullable(entity).map(JpaSupportTicketRepository::toDomain);
  }

  @Override
  public void save(SupportTicket ticket) {
    SupportTicketEntity entity = em.find(SupportTicketEntity.class, ticket.getId());
    boolean isNew = entity == null;
    if (isNew) {
      entity = new SupportTicketEntity();
      entity.id = ticket.getId();
      entity.subject = ticket.getSubject();
      entity.requesterRef = ticket.getRequesterRef();
      entity.channel = ticket.getChannel();
      entity.createdAt = ticket.getCreatedAt();
    }
    entity.priority = ticket.getPriority().wireValue();
    entity.status = ticket.getStatus().wireValue();
    entity.assigneeId = ticket.getAssigneeId();

    if (isNew) {
      em.persist(entity);
    } else {
      entity = em.merge(entity);
    }

    // Append any messages not yet persisted (existing messages are immutable, never re-saved).
    List<String> existingIds = entity.messages.stream().map(m -> m.id).toList();
    for (SupportMessage message : ticket.getMessages()) {
      if (!existingIds.contains(message.getId())) {
        SupportMessageEntity messageEntity = new SupportMessageEntity();
        messageEntity.id = message.getId();
        messageEntity.ticket = entity;
        messageEntity.fromParty = message.getFrom().wireValue();
        messageEntity.author = message.getAuthor();
        messageEntity.body = message.getText();
        messageEntity.createdAt = message.getCreatedAt();
        em.persist(messageEntity);
        entity.messages.add(messageEntity);
      }
    }
  }

  private static SupportTicket toDomain(SupportTicketEntity entity) {
    List<SupportMessage> messages = new ArrayList<>();
    for (SupportMessageEntity m : entity.messages) {
      messages.add(
          new SupportMessage(
              m.id,
              entity.id,
              m.fromParty.equalsIgnoreCase("agent") ? MessageFrom.AGENT : MessageFrom.USER,
              m.author,
              m.body,
              m.createdAt));
    }
    return new SupportTicket(
        entity.id,
        entity.subject,
        entity.requesterRef,
        entity.channel,
        TicketPriority.fromWireValue(entity.priority),
        TicketStatus.fromWireValue(entity.status),
        entity.assigneeId,
        entity.createdAt,
        messages);
  }
}
