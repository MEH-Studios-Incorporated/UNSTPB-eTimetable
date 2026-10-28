-- Copyright (c) 2026 Multimedia Entertainment Hub LLC
-- SPDX-License-Identifier: LicenseRef-Proprietary

-- People who run the dashboard. admin: everything; editor: timetables and
-- announcements, not the admin list.
CREATE TABLE admin (
    id UUID PRIMARY KEY,
    email TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('admin', 'editor')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX admin_email ON admin (lower(email));

CREATE TABLE admin_session (
    token_hash BYTEA PRIMARY KEY,
    admin_id UUID NOT NULL REFERENCES admin (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL
);
