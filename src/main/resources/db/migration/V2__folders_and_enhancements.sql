CREATE TABLE folders (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    owner_id   BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_folders_owner ON folders(owner_id);

ALTER TABLE documents ADD COLUMN folder_id BIGINT REFERENCES folders(id) ON DELETE SET NULL;
CREATE INDEX idx_documents_folder ON documents(folder_id);
