-- Copyright (c) 2026 Multimedia Entertainment Hub LLC
-- SPDX-License-Identifier: LicenseRef-Proprietary

-- Dashboard accounts: disabling, temporary passwords, two-step sign-in.
ALTER TABLE admin
    ADD COLUMN disabled_at TIMESTAMPTZ,
    -- set for a temporary password; the next sign-in asks for a new one
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT false,
    -- TOTP (RFC 6238) secret, and the last time step used so a code works once
    ADD COLUMN totp_secret BYTEA,
    ADD COLUMN totp_enabled_at TIMESTAMPTZ,
    ADD COLUMN totp_last_step BIGINT;

-- verified is false between a correct password and the second step.
ALTER TABLE admin_session
    ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN user_agent TEXT,
    ADD COLUMN ip TEXT,
    ADD COLUMN verified BOOLEAN NOT NULL DEFAULT true;

CREATE UNIQUE INDEX admin_session_id ON admin_session (id);

-- One-time codes for when the phone with the authenticator app is lost.
CREATE TABLE admin_recovery_code (
    admin_id UUID NOT NULL REFERENCES admin (id) ON DELETE CASCADE,
    code_hash BYTEA NOT NULL,
    used_at TIMESTAMPTZ,
    PRIMARY KEY (admin_id, code_hash)
);

-- Every sign-in attempt, for throttling and for the account page.
CREATE TABLE admin_login_attempt (
    id BIGSERIAL PRIMARY KEY,
    at TIMESTAMPTZ NOT NULL DEFAULT now(),
    email TEXT NOT NULL,
    admin_id UUID REFERENCES admin (id) ON DELETE CASCADE,
    ip TEXT,
    user_agent TEXT,
    ok BOOLEAN NOT NULL
);

CREATE INDEX admin_login_attempt_email ON admin_login_attempt (lower(email), at);
CREATE INDEX admin_login_attempt_ip ON admin_login_attempt (ip, at);

-- Who changed what in the dashboard. The email is copied so an entry
-- still reads right after the account is deleted.
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    at TIMESTAMPTZ NOT NULL DEFAULT now(),
    admin_id UUID REFERENCES admin (id) ON DELETE SET NULL,
    admin_email TEXT NOT NULL,
    category TEXT NOT NULL,
    summary TEXT NOT NULL
);

CREATE INDEX audit_log_at ON audit_log (at DESC, id DESC);
