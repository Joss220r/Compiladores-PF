CREATE TABLE IF NOT EXISTS app_users (
    id UUID PRIMARY KEY,
    username VARCHAR(80) UNIQUE NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    display_name VARCHAR(120),
    role VARCHAR(40) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_app_users_username
ON app_users (lower(username));

-- Usuario demo:
-- username: admin
-- password: admin123
INSERT INTO app_users (id, username, password_hash, display_name, role)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'admin',
    '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9',
    'Administrador',
    'ADMIN'
)
ON CONFLICT (username) DO NOTHING;
