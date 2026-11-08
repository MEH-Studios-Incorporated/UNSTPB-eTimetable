-- Copyright (c) 2026 Multimedia Entertainment Hub LLC
-- SPDX-License-Identifier: LicenseRef-Proprietary

CREATE TABLE timetable_report (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES student (id) ON DELETE CASCADE,
    -- Keep the original class even after an import replaces or removes it.
    semester_id TEXT NOT NULL,
    activity_id TEXT NOT NULL,
    snapshot JSONB NOT NULL,
    description TEXT NOT NULL CHECK (char_length(description) BETWEEN 10 AND 2000),
    status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'fixed', 'dismissed')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES admin (id) ON DELETE SET NULL,
    CHECK ((status = 'open' AND reviewed_at IS NULL) OR (status <> 'open' AND reviewed_at IS NOT NULL))
);

CREATE UNIQUE INDEX timetable_report_one_open ON timetable_report (student_id, semester_id, activity_id)
    WHERE status = 'open';
CREATE INDEX timetable_report_student_time ON timetable_report (student_id, created_at DESC);
CREATE INDEX timetable_report_queue ON timetable_report (status, created_at DESC, id DESC);
