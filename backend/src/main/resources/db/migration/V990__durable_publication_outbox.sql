-- Stage 1 durable publication signals.
-- V982-V987 are reserved by the parked provider programme; V990 avoids that merge boundary.

create table platform_outbox_event (
    id              text primary key,
    event_type      text        not null,
    schema_version  integer     not null,
    aggregate_type  text        not null,
    aggregate_id    text        not null,
    payload         jsonb       not null,
    occurred_at     timestamptz not null,
    available_at    timestamptz not null,
    status          text        not null default 'pending',
    attempt_count   integer     not null default 0,
    claimed_until   timestamptz,
    completed_at    timestamptz,
    last_error      text,
    constraint platform_outbox_schema_version_chk check (schema_version > 0),
    constraint platform_outbox_attempt_count_chk check (attempt_count >= 0),
    constraint platform_outbox_status_chk
        check (status in ('pending', 'processing', 'completed', 'failed')),
    constraint platform_outbox_completion_chk check (
        (status = 'completed' and completed_at is not null)
        or (status <> 'completed' and completed_at is null)
    )
);

-- Queue scans only need incomplete, claimable rows. Completed history never bloats this index.
create index platform_outbox_claim_idx
    on platform_outbox_event (available_at, occurred_at, id)
    where status in ('pending', 'processing');

create index platform_outbox_aggregate_idx
    on platform_outbox_event (aggregate_type, aggregate_id, occurred_at);

create table platform_inbox_receipt (
    consumer_name text        not null,
    event_id      text        not null,
    processed_at  timestamptz not null,
    primary key (consumer_name, event_id),
    constraint platform_inbox_event_fk foreign key (event_id)
        references platform_outbox_event (id) on delete restrict
);

create index platform_inbox_event_idx on platform_inbox_receipt (event_id);
