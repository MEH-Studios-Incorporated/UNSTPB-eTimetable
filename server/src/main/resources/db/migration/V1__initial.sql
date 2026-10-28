-- Copyright (c) 2026 Multimedia Entertainment Hub LLC
-- SPDX-License-Identifier: LicenseRef-Proprietary

-- A semester and everything in it is stored as the shared model's JSON
-- (shared/.../model). One faculty has a few thousand rows at most, and the
-- app always reads a semester whole.
CREATE TABLE semester (
    id TEXT PRIMARY KEY,
    data JSONB NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT FALSE,
    -- raised on every change to the semester or its items; part of the ETag
    version BIGINT NOT NULL DEFAULT 1
);

CREATE UNIQUE INDEX semester_one_current ON semester (is_current) WHERE is_current;

-- kind is one of: programme, series, group, half_group, subject, professor,
-- building, room, activity
CREATE TABLE dataset_item (
    semester_id TEXT NOT NULL REFERENCES semester (id) ON DELETE CASCADE,
    kind TEXT NOT NULL,
    id TEXT NOT NULL,
    -- the order the admin entered them in, which lists show
    position INTEGER NOT NULL,
    data JSONB NOT NULL,
    PRIMARY KEY (semester_id, kind, id)
);

CREATE TABLE announcement (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    author TEXT NOT NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX announcement_newest ON announcement (published_at DESC, id DESC);

CREATE TABLE student (
    id UUID PRIMARY KEY,
    email TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    profile JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX student_email ON student (lower(email));

-- Only a SHA-256 of each token is kept, so a database leak does not leak sessions.
CREATE TABLE student_session (
    token_hash BYTEA PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES student (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
