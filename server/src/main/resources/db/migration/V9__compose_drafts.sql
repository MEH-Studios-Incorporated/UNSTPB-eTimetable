-- Copyright (c) 2026 Multimedia Entertainment Hub LLC
-- SPDX-License-Identifier: LicenseRef-Proprietary

-- An announcement or event saved from its compose page before it is
-- published. Drafts are only for the dashboard: the apps and the public API
-- never read this table. Every dashboard account sees every draft.
CREATE TABLE compose_draft (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kind TEXT NOT NULL CHECK (kind IN ('announcement', 'event')),
    -- the form's fields as typed, {"title": "...", "body": "...", ...}
    form JSONB NOT NULL,
    -- an event's picture, kept in event_image; the cleanup of unused
    -- pictures skips the ones a draft holds
    image_hash TEXT REFERENCES event_image (hash),
    -- emails, so the list still says who wrote it after an account is deleted
    created_by TEXT NOT NULL,
    updated_by TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX compose_draft_by_kind ON compose_draft (kind, updated_at DESC);
