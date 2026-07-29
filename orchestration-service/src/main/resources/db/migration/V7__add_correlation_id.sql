ALTER TABLE transfers ADD COLUMN correlation_id VARCHAR(64);
ALTER TABLE outbox_events ADD COLUMN correlation_id VARCHAR(64);