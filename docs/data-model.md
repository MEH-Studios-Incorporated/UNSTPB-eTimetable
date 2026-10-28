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
activities they picked (Erasmus), plus their extra slots. A national student
who has not chosen a half-group sees the labs of both halves of their group
and picks.

## People and announcements

Student
: id, email, password hash, display name, enrollment (national or Erasmus),
  group or half-group (national), picked activity ids (Erasmus), extra
  slots (an activity id, with a date when it is a one-time visit, used by
  national students to attend or recover another group's lab), preferred
  language, push tokens with their platform.

Admin
: id, email, password hash, role (admin or editor).

Announcement
: id, title, body (Markdown), published at, author (an admin), optional
  pinned flag. Every student sees every announcement.

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
announcements with a read flag, and the profile with a flag for changes
not yet sent to the server. The statements stay within SQLite 3.18, the
version on Android 8.

The sample semester used by the tests is in
`shared/src/commonTest/.../SampleData.kt`: invented names on FAIMA's code
formats, with Wednesday 28 October 2026 in week 5, as on the mockups.

## API (version 1)

| Method and path | What it returns |
| --- | --- |
| `GET /api/v1/dataset` | The current semester with structure, subjects, professors, rooms and activities. `ETag` for caching. |
| `GET /api/v1/announcements?before=<id>` | Announcements, newest first, 20 per page. |
| `POST /api/v1/auth/signup`, `/login`, `/logout` | Session token. |
| `GET`, `PUT /api/v1/me` | The student's profile. Erasmus picks are checked for overlaps. |
| `DELETE /api/v1/me` | Deletes the account and everything stored for it. |
| `PUT`, `DELETE /api/v1/me/push-tokens/<token>` | Register or remove a device. |

The dashboard lives under `/admin` and is not part of the API.
