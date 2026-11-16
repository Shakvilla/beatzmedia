alter table platform_outbox_event
    add column replay_count integer not null default 0,
    add column last_replayed_at timestamptz,
    add column last_replayed_by text,
    add constraint platform_outbox_replay_count_chk check (replay_count >= 0),
    add constraint platform_outbox_replay_metadata_chk check (
        (last_replayed_at is null and last_replayed_by is null)
        or (last_replayed_at is not null and last_replayed_by is not null)
    );

create index platform_outbox_ops_idx
    on platform_outbox_event (status, occurred_at desc, id);
