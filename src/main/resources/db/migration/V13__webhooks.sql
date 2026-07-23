CREATE TABLE webhooks (
    id BIGSERIAL PRIMARY KEY,
    url VARCHAR(500) NOT NULL,
    events TEXT NOT NULL DEFAULT '[]',       -- JSON array e.g. ["UPLOAD","DELETE"]
    secret VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
