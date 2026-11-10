-- Copyright (c) 2026 Multimedia Entertainment Hub LLC
-- SPDX-License-Identifier: LicenseRef-Proprietary

-- Pictures of events, re-encoded as JPEG by the server. They live in the database
-- so the nightly pg_dump keeps them with everything else.
CREATE TABLE event_image (
    -- SHA-256 of data, in hex; also the picture's address, so it never changes
    hash TEXT PRIMARY KEY,
    data BYTEA NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE event (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    place TEXT,
    link TEXT,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ,
    image_hash TEXT NOT NULL REFERENCES event_image (hash),
    published_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX event_by_start ON event (starts_at);
