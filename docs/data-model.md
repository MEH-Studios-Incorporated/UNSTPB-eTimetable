# Data model

The first version of the entities the app, the server and the dashboard
share. The Kotlin types live in `shared/`; the server's tables follow them.
Names are in English in code; everything shown to people has a Romanian and
an English text.

## Calendar

Semester
: id, name (RO, EN), first day, last day, and a list of breaks (holidays,
  exam sessions), each a date range with a name.

Teaching weeks are numbered from 1 starting on the semester's first Monday.
Weeks that fall entirely inside a break get no number, so week 8 after a
one-week break is still the eighth week of classes. A week is odd or even
by its number. The admin can override the parity of a single week, because
faculties sometimes shift it around public holidays.

## Structure

Programme
: id, name (RO, EN), level (bachelor, master), language of teaching.

Year
: a programme and a study year (1 to 4 for bachelor).

Series
: a group of groups that attend courses together, inside one year.

Group
: id, code as the faculty writes it (for example `1521`), series.

HalfGroup
: a group split for labs, with a suffix (`1521a`, `1521b`). Optional; a
  group without halves attends labs as a whole.

## Teaching

Subject
: id, code, name (RO, EN), programme, year, semester.

Professor
: id, academic title, name, email, office (room), and office hours as a
  list of weekly slots with the same week rules as activities.

Building
: id, code, name, address, coordinates for the map link.

Room
: id, building, code (`BN 030`), capacity, optional floor.

Activity
: one recurring slot in the timetable.
  - subject, type (course, seminar, lab, project), professor, room
  - weekday, start time, end time
  - weeks: all, odd, even, or an explicit list of week numbers
  - audience: a list of series, groups or half-groups
  - open to Erasmus students: yes or no

A student's timetable is never stored as such. It is computed from the
activities whose audience contains their group (national) or from the
activities they picked (Erasmus), plus their extra slots, minus the slots of
their group they dropped. A national student who has not chosen a half-group
sees the labs of both halves of their group and picks.

## People and announcements

Student
: id, email, password hash, first name, last name, phone number (all three
  required, the phone checked only for its shape), enrollment (national or Erasmus),
  group or half-group (national), picked activity ids (Erasmus), extra
  slots (an activity id, with a date when it is a one-time visit, used by
  national students to attend or recover another group's lab), dropped slots
  (activity ids of their own group they no longer attend because they go to
  another group's instead; the app keeps at least one weekly slot of each
  kind per subject, and changing the group clears the list), preferred
  language, push tokens with their platform (each tied to the session that
  registered it, at most 10 per student, removed when the session ends).

Admin
: id, email, password hash, role (admin or editor), whether it is disabled,
  whether the password is a temporary one to change at the next sign-in, and
  the TOTP secret with the last code step used when two-step sign-in is on.
  Sessions keep their browser, address, start and last activity; every
  sign-in attempt is kept 180 days for the lock and the account page; ten
  recovery codes are stored hashed.

Dashboard setting
: a key and a value, with who changed it and when. The only one so far is
  `require_two_step`.

Audit log
: time, admin (the email is copied, so entries outlive the account),
  category (accounts, timetable, announcements, events, reports, students)
  and a sentence in Romanian. Entries are only ever added.

Import draft
: id, semester, admin, creation time, file name, file text and the column
  mapping (a sheet column to its position in the file, as JSON). It holds a
  timetable file between the steps of the import wizard and is deleted when
  the import is confirmed or cancelled; drafts older than 24 hours are
  deleted when a new one is made and cannot be opened.

API token
: id, name, scope (`timetable:write` is the only one), SHA-256 of the
  token, creation time, creator (the email is copied), optional expiry,
  last use and revocation time. The token itself (`ett_` and 43 characters)
  is shown once and never stored.

Announcement
: id, title, body (Markdown), published at, author (an admin), optional
  pinned flag. Every student sees every announcement.

Event
: id, title, start, optional end, optional place, body (Markdown, may be
  empty), optional link, picture, the picture's width divided by its height,
  published at. Every student sees every event; the apps get those that end
  less than 60 days ago. The picture is a path,
  `/api/v1/images/<sha-256>.jpg`: the server re-encodes every upload as JPEG
  at most 1600 px on its long side (which drops the camera's metadata,
  location included) and stores it in PostgreSQL by its hash, so a new
  picture is a new address and an old one can be cached for good. A picture
  no event uses is deleted.

Timetable report
: id, student id, semester id, activity id, description, class snapshot,
  creation time, status (open, fixed or dismissed), and closure time and
  administrator. The server creates the snapshot from the current dataset,
  including the subject, slot, teacher, room and audience. It survives
  timetable edits and imports. Reports belong to the student's account and
  are deleted with it; only dashboard staff can read them.

Descriptions are trimmed and must contain 10 to 2,000 characters. Only
activities in the current semester can be reported. An account has at most
one open report for the same semester and activity, and can submit five
reports in any rolling hour, including reports closed during that hour.
Closing a report changes its status, not the class. Correcting a class uses
the existing activity editor and dataset sync.

## Rules computed in `shared/`

- Week number and parity for a date, with breaks and overrides.
- The occurrences of an activity in a date range.
- "Now" and "next" for a student or a professor at a given moment.
- Overlap: two slots overlap when they share a weekday, at least one week,
  and a time range. Back-to-back slots (one ends at 10:00, the next starts at
  10:00) do not overlap. Erasmus picks that overlap are refused with the
  clashing slot named.
- Where a professor is: the activity or office hour happening now, else the
  next one today.

## On the phone

`shared/` keeps an offline copy in SQLite (`Etimetable.sq`): the dataset as
one JSON document with its `ETag` and the time it was last checked,
announcements with a read flag, the events as one JSON list with its
`ETag`, and the profile with a flag for changes not yet sent to the
server. The statements stay within SQLite 3.18, the version on Android 8.
Event pictures are not in SQLite; the app's image cache keeps them.

A change to `Etimetable.sq` reaches installed apps only through a migration:
add `N.sqm` next to it with the statements that turn version N into the new
schema, and run `./gradlew :shared:generateCommonMainEtimetableDatabaseSchema`
before the change to keep `databases/N.db`, the old schema.
`:shared:verifySqlDelightMigration`, which CI runs, checks that every `.db`
plus its migrations gives the current schema. Version 1 is the first
release; version 2 added the events.

The sample semester used by the tests is in
`shared/src/commonTest/.../SampleData.kt`: invented names on FAIMA's code
formats, with Wednesday 28 October 2026 in week 5, as on the mockups.

## API (version 1)

| Method and path | What it returns |
| --- | --- |
| `GET /api/v1/dataset` | The current semester with structure, subjects, professors, rooms and activities. `ETag` for caching. |
| `GET /api/v1/announcements?before=<id>` | Announcements, newest first, 20 per page; `pinned=true` pages only pinned posts. |
| `GET /api/v1/events` | Events that end less than 60 days ago, earliest first. `ETag` for caching. |
| `GET /api/v1/images/<hash>.jpg` | An event's picture, `Cache-Control: public, max-age=31536000, immutable`. |
| `POST /api/v1/auth/signup`, `/login`, `/logout` | Session token. |
| `GET`, `PUT /api/v1/me` | The student's profile. Erasmus picks are checked for overlaps. `PUT` answers with the profile as stored: classes an import removed since the last save are dropped from it, and an old group that an import removed is kept until the student picks another. |
| `DELETE /api/v1/me` | Deletes the account and everything stored for it. |
| `POST /api/v1/timetable-reports` | Signed-in student submits `semesterId`, `activityId`, `description`; returns `201` with the report `id`. |
| `PUT`, `DELETE /api/v1/me/push-tokens/<token>` | Register or remove a device. |

Registering a device sends its platform: `{"platform": "android"}` or
`{"platform": "ios"}`. The token is at most 1024 characters (`400`
otherwise). More than 10 registrations by one student in an hour get `429`
with a `Retry-After` header.

The dashboard lives under `/admin` and is not part of the API.

## Admin API (version 1)

For programs that send the timetable without the browser. Every call needs
`Authorization: Bearer <token>` with a token from the dashboard's Tokenuri
API page (`docs/admin.md`); a missing, unknown, revoked or expired token
gets `401` with `unauthorized`.

| Method and path | What it does |
| --- | --- |
| `POST /api/admin/v1/semesters/<id>/timetable` | Body: the timetable as CSV (`text/csv`) or an Excel workbook (`.xlsx`, first sheet), at most 5 MB, the format in `docs/admin.md`. Needs `timetable:write`. With `?dry_run=true` returns the difference and saves nothing; without it replaces the semester's timetable. |

Columns are matched by name as in the dashboard's import (case and
diacritics do not matter, Romanian names work). The answer is JSON:

```json
{
  "semesterId": "2026-1",
  "dryRun": true,
  "saved": false,
  "version": 12,
  "counts": {"added": 1, "changed": 1, "removed": 1, "unchanged": 7, "errors": 0},
  "changes": [
    {"kind": "changed", "activityId": "micro-c", "line": 2,
     "fields": [{"field": "room", "old": "AN015", "new": "EC210"}]}
  ],
  "errors": [{"line": 5, "message": "zi necunoscută \"vinerea\""}],
  "warnings": ["sala AN015 are două activități în același timp: micro-c și calit-c"]
}
```

`kind` is `added`, `changed` or `removed`; `line` is the file line (null
for a removed class, or for an error about the whole file). `fields` lists
what differs, as the dashboard shows it, under the names `subject`, `type`,
`time`, `weeks`, `audience`, `professor`, `room` and `erasmus`; an added
class has every field with `old` null, a removed one every field with `new`
null. `version` is the semester's version after the call. Every save is
recorded in the dashboard's log under the token's name.

Errors: `404` with `no_semester` for an unknown semester; `409` with
`invalid_timetable` when the file has errors (`details` has one line per
error, nothing is saved); `409` with `timetable_changed` when the semester
changed while the file was checked (send it again); `403` with `forbidden`
for a token without the scope; `413` for a file over 5 MB.

Report submissions return `422` for an invalid description or stale class,
`409` with `report_already_open` for a duplicate open report, and `429`
with `too_many_requests` and a `Retry-After` header when the hourly
allowance is used. The API does not expose a list of reports.
