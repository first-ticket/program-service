CREATE TABLE p_outbox
(
    id             UUID         NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(50)  NOT NULL,
    payload        JSON,
    status         VARCHAR(20)  NOT NULL,
    published_at   TIMESTAMP,
    retry_count    INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP,
    deleted_at     TIMESTAMP,

    CONSTRAINT pk_outbox PRIMARY KEY (id),
    CONSTRAINT uq_outbox_correlation_event UNIQUE (correlation_id, event_type)
);

CREATE INDEX idx_outbox_status_created ON p_outbox (status, created_at);

CREATE TABLE p_inbox
(
    message_id   UUID      NOT NULL,
    processed_at TIMESTAMP NOT NULL,

    CONSTRAINT pk_inbox PRIMARY KEY (message_id)
);
