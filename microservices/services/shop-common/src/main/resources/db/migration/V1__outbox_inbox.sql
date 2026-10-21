-- Outbox: o evento e gravado na MESMA transacao do dado de negocio e publicado no Kafka depois.
-- Assim nao existe o caso "salvou no banco mas o evento se perdeu".
CREATE TABLE outbox_event (
    id           UUID         PRIMARY KEY,
    topic        VARCHAR(120) NOT NULL,
    event_key    VARCHAR(120) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON outbox_event (created_at) WHERE published_at IS NULL;

-- Inbox: eventos ja processados por cada consumidor (entrega do Kafka e "ao menos uma vez").
CREATE TABLE processed_event (
    id           VARCHAR(160) PRIMARY KEY,
    processed_at TIMESTAMPTZ  NOT NULL
);
