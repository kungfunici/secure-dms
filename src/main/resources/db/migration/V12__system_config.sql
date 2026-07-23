CREATE TABLE system_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(255) NOT NULL UNIQUE,
    config_value TEXT,
    description VARCHAR(500),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO system_config (config_key, config_value, description) VALUES
    ('max-file-size', '50', 'Maximum upload file size in MB'),
    ('default-version-retention-days', '30', 'Default version retention for new users'),
    ('retention-job-enabled', 'true', 'Enable automatic retention policy enforcement'),
    ('signup-enabled', 'true', 'Allow new user registration');
