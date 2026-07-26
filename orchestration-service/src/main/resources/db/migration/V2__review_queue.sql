-- V2__review_queue.sql — orchestration-service

CREATE TABLE review_queue_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id     UUID NOT NULL REFERENCES transfers(id),
    risk_score      INTEGER NOT NULL,
    reason          VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, DECLINED
    reviewed_by     VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at     TIMESTAMPTZ
);

CREATE INDEX idx_review_queue_status ON review_queue_entries(status);
CREATE INDEX idx_review_queue_transfer_id ON review_queue_entries(transfer_id);