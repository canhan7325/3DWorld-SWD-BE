-- Durable one-time tokens and their email delivery/retry state.
CREATE TABLE auth_email_tokens (
    token_id UUID PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(user_id),
    recipient VARCHAR(320) NOT NULL,
    purpose VARCHAR(30) NOT NULL CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    encrypted_token TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED', 'CANCELLED')),
    delivery_attempts INTEGER NOT NULL DEFAULT 0 CHECK (delivery_attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at TIMESTAMPTZ,
    last_error VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_email_token_expiry CHECK (expires_at > created_at)
);
CREATE INDEX ix_email_tokens_user_purpose ON auth_email_tokens(user_id, purpose, created_at DESC);
CREATE INDEX ix_email_tokens_delivery ON auth_email_tokens(next_attempt_at, created_at)
    WHERE delivery_status IN ('PENDING', 'FAILED');
