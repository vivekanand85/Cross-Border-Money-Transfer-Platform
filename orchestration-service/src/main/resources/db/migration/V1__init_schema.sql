-- V1__init_schema.sql — orchestration-service

CREATE TABLE transfers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key     VARCHAR(100) NOT NULL UNIQUE,
    current_state       VARCHAR(30) NOT NULL,
    amount              BIGINT NOT NULL CHECK (amount > 0),
    currency            VARCHAR(3) NOT NULL,
    source_account_id   UUID NOT NULL,
    dest_account_id     UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transfers_idempotency_key ON transfers(idempotency_key);
CREATE INDEX idx_transfers_current_state ON transfers(current_state);

-- Append-only audit trail. No updated_at, no update path — same philosophy as ledger_entries.
CREATE TABLE transfer_state_transitions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id    UUID NOT NULL REFERENCES transfers(id),
    from_state     VARCHAR(30),
    to_state       VARCHAR(30) NOT NULL,
    triggered_by   VARCHAR(50) NOT NULL,
    reason         VARCHAR(500),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transitions_transfer_id ON transfer_state_transitions(transfer_id);

-- Transactional outbox. Written in the SAME DB transaction as the state
-- change it describes — guarantees the event is never lost even if the
-- message broker publish step fails or the process crashes right after commit.
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_status ON outbox_events(status);