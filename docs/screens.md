# Screens

What the apps show, screen by screen, derived from [brief.md](brief.md).
FAIMA's web prototype was only a loose picture of the idea; where this list
differs from it, this list is right.

## Navigation

A bottom bar with four tabs: Timetable, Subjects, Professors,
Announcements. The profile opens from the top bar on every tab. Tapping a
subject, room or professor anywhere opens its page.

## First start

1. Sign in with email and password, or create an account with first name,
   last name, email, phone number and the password typed twice. A "remember
   me" box keeps the session; without it the session ends when the app is
   closed. The FAIMA seal sits above the form; the footer is a small
   Multimedia Entertainment Hub credit (mark and copyright, no links; those
   are on the About page). A RO / EN switch in the top
   corner changes the language before signing in.
2. A forgotten password is reset by email when the server has email
   configured, otherwise by an admin from the dashboard.
3. Enrollment: national or Erasmus.
4. National: programme, year, group, half-group if the group has halves.
   The programmes at FAIMA today are IMA, BEM, IEDEEE, IEICM and IBM.
5. Erasmus: search subjects across all programmes and years and add them,
   then pick a course and a lab slot for each one. A slot that overlaps one
   already picked is refused, naming the clash.
6. Notifications: ask for permission, explaining that announcements arrive
   this way.

Every choice can be changed later from the profile. Nothing is checked.

## Timetable

- Three layouts, chosen in the profile (Orar, Aspect); each has a day and
  a week view, and the profile also sets which of the two opens first:
  - Timeline (default): the day on an hour scale with a line at the
    current time; the week as columns of hours, Monday to Friday, Saturday
    only when it has something.
  - List: a card per class with the breaks between them written out
    ("Pauză · 4 h"); the week as an agenda, day by day, free days marked.
  - Table: one dense line per class (time, type letter, name, room); the
    week as a table of starting times by days, like the faculty's sheet.
- Above the day view, the next activity with its room and how long until
  it starts.
- The header shows the date range, the teaching week number and whether it
  is odd or even. Breaks are labelled as such instead of an empty week.
- The top bar always shows the current teaching week as a small ticket:
  the number in a solid block, "Săptămâna impară" beside it; "Vacanță"
  during a break. On Saturday and Sunday it shows the coming week.
- In the week view the arrows to the previous and next week sit next to
  the dates.
- A class card shows as much as its height allows: two hours or more get
  type, name on two lines, time, room and professor; an hour gets the name
  and one line with time, type and room.
- Swipe between days or weeks; a "today" control jumps back.
- Tapping an activity opens a sheet: subject, type, time, room and building
  (with a map link), who runs it, which weeks it runs, which groups attend.
  A course names its professor; a seminar, lab or project names its
  assistant and, below, the professor of the subject's course for the same
  series. When the timetable names nobody the sheet says so. Every name
  opens that professor's page.

## Subjects

- A searchable list of all subjects, filtered by default to the student's
  programme and year, with a switch to see all.
- The subject page lists every activity grouped by type (course, seminar,
  lab, project): day, time, weeks, room, professor, groups.
- "Fits my timetable" filters the list to slots that do not overlap
  anything the student already has. This is how a national student finds a
  lab to recover or another group's lab to attend.
- A national student can add a slot to their own timetable, once (a single
  date) or for the rest of the semester. An Erasmus student adds and
  removes their picks here. Overlaps are refused in both cases.

## Professors

- A searchable list with each professor's current status in one line:
  teaching (subject, room, until when), office hours (room, until when),
  next activity today, or nothing more today.
- The professor page: status, the week's schedule (the coming week on a
  weekend), office hours, office, email.

## Announcements

- Previews, newest first, pinned ones on top, unread ones marked: title, two
  lines of text, date and author.
- Tapping one opens it in full, with its Markdown (paragraphs, lists, bold,
  italic, links) formatted, and marks it read.
- A push notification opens the announcement directly.

## Profile and settings

The profile is a menu, like the phone's own settings: a header with the
student's initials, name, email and phone, then grouped rows that show
their current value and open the next step:

- Grupa și orarul: change group or subjects (shows the group, or the number
  of Erasmus picks), update now (with the time of the last update, or that
  a change waits to be sent).
- Aplicația: Setări and Despre aplicație, each its own page.
- Sign out, in red, on its own.

Setări has two groups. General: language (same as the phone, Română or
English; on Android 13 and newer this is the same setting as the phone's
per-app language) and theme (same as the phone, light, dark). Orar: the
layout and the view it opens on. Each row opens a sheet with the options
and a short line about each. Despre aplicație has the FAIMA seal, the
version, the privacy note, the FAIMA site and the Multimedia Entertainment
Hub credit.

Pages opened over a tab (profile, a professor, a subject, an announcement,
changing the group) have the same top bar: a round back button and the
page's name, which stay in place while the page scrolls.

Changing the language fades the screen out and back in, since the app is
drawn again in the new language.

## About and legal

Opened from the profile. It shows the FAIMA seal, the app version and the
last sync, then:

- Privacy policy: what the server keeps (email, password hash, name, phone
  number, enrollment, group or picked slots, language, push tokens) and what it
  does not (location, contacts, analytics or tracking), how to delete the
  account.
- Terms of use: the timetable is for information; the faculty's official
  announcements prevail.
- Open source licenses: generated at build time from the dependencies,
  plus Libre Franklin (SIL Open Font License).
- Report a problem: a form that sends a description and an optional
  screenshot.
- FAIMA's website.

Then the MEH mark next to "Multimedia Entertainment Hub LLC" and "© 2026.
All rights reserved.", links to https://mehstudios.net/ and to
support@mehstudios.net, and a line saying the app was commissioned by
FAIMA, UNSTPB, and that the FAIMA and UNSTPB names and logos belong to the
university.

## Offline

After the first sync every screen works without a connection. A thin bar
says "Offline, timetable from <date and time>" while there is no
connection; nothing else changes.

## Widgets

| Size | Shows |
| --- | --- |
| Small | The next activity: time, subject, room |
| Medium | The rest of today |
| Large | The rest of today and the start of tomorrow |

Tapping a widget opens the app on that activity or day.

## Admin dashboard (web, part of the server)

Sign in, semester calendar, programmes and groups, subjects, professors and
office hours, buildings and rooms, activities on a week grid per group, CSV
import, announcements (write, preview, publish, pin), admin accounts.
