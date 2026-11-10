-- Copyright (c) 2026 Multimedia Entertainment Hub LLC
-- SPDX-License-Identifier: LicenseRef-Proprietary

-- One row per phone that receives announcements. A token belongs to the
-- session that registered it last, so a phone that changes account moves,
-- and signing out or losing the session removes it.
CREATE TABLE push_token (
    token TEXT PRIMARY KEY,
    session_token_hash BYTEA NOT NULL
        REFERENCES student_session (token_hash) ON DELETE CASCADE,
    platform TEXT NOT NULL CHECK (platform IN ('android', 'ios')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX push_token_session ON push_token (session_token_hash);
