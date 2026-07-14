CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    email         VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'ROLE_USER',
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email    ON users(email);

CREATE TABLE documents (
    id                BIGSERIAL PRIMARY KEY,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename   VARCHAR(255) NOT NULL UNIQUE,
    content_type      VARCHAR(100) NOT NULL,
    file_size         BIGINT       NOT NULL,
    description       VARCHAR(1000),
    owner_id          BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    uploaded_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documents_owner    ON documents(owner_id);
CREATE INDEX idx_documents_filename ON documents(original_filename);

CREATE TABLE document_permissions (
    id              BIGSERIAL PRIMARY KEY,
    document_id     BIGINT      NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    user_id         BIGINT      NOT NULL REFERENCES users(id)     ON DELETE CASCADE,
    permission_type VARCHAR(20) NOT NULL,
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_doc_user UNIQUE (document_id, user_id)
);

CREATE INDEX idx_permissions_document ON document_permissions(document_id);
CREATE INDEX idx_permissions_user     ON document_permissions(user_id);

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    action      VARCHAR(50)  NOT NULL,
    user_id     BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    document_id BIGINT,
    details     VARCHAR(500),
    ip_address  VARCHAR(45),
    timestamp   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user      ON audit_logs(user_id);
CREATE INDEX idx_audit_document  ON audit_logs(document_id);
CREATE INDEX idx_audit_timestamp ON audit_logs(timestamp DESC);
