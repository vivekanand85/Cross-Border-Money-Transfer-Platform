-- V4__settlement_and_recon.sql — orchestration-service

-- Populated by the Kafka consumer reading transfer-events. This is the
-- FIRST thing in the whole project that actually reads from the topic —
-- Week 2 only proved publishing worked.
CREATE TABLE settled_transfers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id     UUID NOT NULL,
    to_state        VARCHAR(30) NOT NULL,
    event_payload   JSONB NOT NULL,
    consumed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_settled_transfers_transfer_id ON settled_transfers(transfer_id);
CREATE INDEX idx_settled_transfers_to_state ON settled_transfers(to_state);

-- Flagged mismatches between our Ledger records and the simulated vendor
-- settlement report.
CREATE TABLE recon_exceptions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id         UUID,
    exception_type      VARCHAR(50) NOT NULL, -- AMOUNT_MISMATCH, MISSING_IN_LEDGER, MISSING_IN_VENDOR_REPORT
    expected_amount     BIGINT,
    actual_amount       BIGINT,
    details             VARCHAR(500),
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- OPEN, RESOLVED
    detected_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_recon_exceptions_status ON recon_exceptions(status);