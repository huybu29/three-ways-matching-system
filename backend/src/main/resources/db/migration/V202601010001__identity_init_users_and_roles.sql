CREATE TABLE app_user (
    id            UUID         PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT chk_app_user_username_len CHECK (char_length(username) >= 3),
    CONSTRAINT chk_app_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE user_role (
    user_id UUID        NOT NULL,
    role    VARCHAR(30) NOT NULL,

    CONSTRAINT pk_user_role PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT chk_user_role_value CHECK (role IN ('ADMIN', 'MATCHER', 'APPROVER', 'POSTER', 'AUDITOR'))
);

CREATE INDEX idx_user_role_role ON user_role (role);