CREATE TABLE retention_policies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    document_type VARCHAR(50),
    folder_id BIGINT REFERENCES folders(id) ON DELETE SET NULL,
    retention_days INTEGER NOT NULL CHECK (retention_days > 0),
    action VARCHAR(20) NOT NULL DEFAULT 'DELETE' CHECK (action IN ('DELETE', 'ARCHIVE')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE legal_holds (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    reason TEXT NOT NULL,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    released_at TIMESTAMPTZ
);

CREATE INDEX idx_legal_holds_document ON legal_holds(document_id);
CREATE INDEX idx_legal_holds_active ON legal_holds(document_id) WHERE released_at IS NULL;

ALTER TABLE documents ADD COLUMN retention_at TIMESTAMPTZ;
ALTER TABLE documents ADD COLUMN legal_hold BOOLEAN NOT NULL DEFAULT FALSE;
