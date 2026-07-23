-- E2EE key management for client-side encryption
-- The server stores only public keys and wrapped document keys.
-- Private keys never leave the client.

CREATE TABLE user_keys (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    public_key TEXT NOT NULL,          -- RSA/OIDC public key (SPKI base64)
    key_algorithm VARCHAR(20) NOT NULL DEFAULT 'RSA-OAEP',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE document_keys (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    wrapped_key BYTEA NOT NULL,        -- AES key encrypted with user's public key
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_doc_user_key UNIQUE (document_id, user_id)
);

CREATE INDEX idx_user_keys_user ON user_keys(user_id);
CREATE INDEX idx_document_keys_document ON document_keys(document_id);
CREATE INDEX idx_document_keys_user ON document_keys(user_id);
