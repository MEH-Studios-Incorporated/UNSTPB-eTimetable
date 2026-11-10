-- Copyright (c) 2026 Multimedia Entertainment Hub LLC
-- SPDX-License-Identifier: LicenseRef-Proprietary

-- A timetable file between the steps of the import wizard. Nothing in it is
-- part of the timetable until the admin confirms. Drafts older than 24 hours
-- are deleted when a new one is made.
CREATE TABLE import_draft (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    semester_id TEXT NOT NULL REFERENCES semester (id) ON DELETE CASCADE,
    admin_id UUID NOT NULL REFERENCES admin (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    file_name TEXT NOT NULL,
    file_text TEXT NOT NULL,
    -- {"day": 3, ...}: a column of the sheet to its position in the file
    mapping JSONB NOT NULL
);

CREATE INDEX import_draft_created_at ON import_draft (created_at);

-- Tokens with which a program sends a timetable without the browser. Only
-- the SHA-256 of the token is kept; the token itself is shown once. A
-- revoked token stays listed with the time it was revoked.
CREATE TABLE api_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    token_hash BYTEA NOT NULL UNIQUE,
    scope TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID REFERENCES admin (id) ON DELETE SET NULL,
    -- copied, so the list still says who made it after the account is deleted
    created_by_email TEXT NOT NULL,
    expires_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);
