# UNSTPB eTimetable

A timetable app for students of FAIMA (the Faculty of Entrepreneurship,
Business Engineering and Management) at the National University of Science
and Technology POLITEHNICA Bucharest. It shows your classes, where each
professor is and the faculty's announcements, on Android and iOS, and keeps
working offline once the timetable has synced.

Developed and owned by Multimedia Entertainment Hub LLC, on commission from
FAIMA, UNSTPB.

Status: early development. The Android app shows a student's timetable from a
running server, with its subjects, professors, the faculty's events, a home
screen widget and push notifications for new announcements and events; the
iOS app is next.

## What it does

- National students pick their group once and get the timetable the faculty
  publishes for it.
- Erasmus students build their own timetable from the subjects in their
  learning agreement, across specializations and years. The app refuses a
  slot that overlaps one already picked.
- Every subject has a page with all its course and lab slots, rooms and
  professors, so you can go to another group's lab or recover a missed one.
- A professor timetable: what each professor teaches, where and when, and
  their office hours.
- Faculty announcements with push notifications.
- Faculty events as cards with a picture: what, when and where, a
  description and a link, published from the dashboard.
- In-browser schedule search and bulk room, professor and time changes,
  with a preview before saving. Android has a professor directory and
  announcement search.
- Report an incorrect class from its details. The secretariat reviews the
  report in the dashboard and can correct the class or dismiss the report.
- Home screen widgets with today's classes and the next one.
- Native apps: Jetpack Compose on Android, SwiftUI on iOS, with the
  timetable logic shared in Kotlin.

## Screens

The Android app on FAIMA's timetable, in the light and the dark theme. The
events are samples.

<p>
<img src="design/screenshots/today-light.webp" width="190" alt="A day of group 1521A on the timeline: German, statistics and communication seminars with their rooms">
<img src="design/screenshots/week-dark.webp" width="190" alt="The week as a table of times and days, in the dark theme">
<img src="design/screenshots/events-light.webp" width="190" alt="The Events tab: cards with a picture, the title and the day, time and place">
<img src="design/screenshots/event-dark.webp" width="190" alt="An event's page in the dark theme: the picture fades into the page under the title">
</p>

The approved designs we started from, ten screens including the widgets and
the sign-in and About pages, are in [design/mockups](design/mockups). The
colours, type and logo rules are in [docs/design.md](docs/design.md).

## Why we are making it

FAIMA asked us, the developers at Multimedia Entertainment Hub, for a
timetable app. The prototype we were shown had the right screens, but it was
a web page and looked generated.

We want an app that opens instantly, works in a lecture hall with no signal,
and looks like something the faculty would put its name on.

## What works

- The Android app: sign-in, choosing a group or Erasmus slots (clashes are
  refused on the spot), today and week views with the next class and the
  current time, class details, the subjects with every slot (add another
  group's lab once or for the semester), professors, announcements, events
  with their pictures, and the profile, with three timetable layouts and a home screen widget in three
  sizes. It keeps working offline and updates itself every few hours. New
  announcements arrive as notifications once the build has a Firebase project
  (see [CONTRIBUTING.md](CONTRIBUTING.md)).
- The server stores semesters, announcements and student accounts in
  PostgreSQL and serves them over its API, with Erasmus overlaps checked on
  the server too. It sends each new announcement to the registered Android
  phones through Firebase Cloud Messaging. It runs from a Docker image next
  to PostgreSQL.
- The faculty's dashboard at `/admin`: semesters and their holidays, the
  timetable imported from one spreadsheet (and exported back to it),
  announcements, events with their pictures, timetable problem reports and
  dashboard accounts.
- The shared Kotlin code computes timetables, week numbers and clashes, and
  keeps an offline copy on the phone.
- The iOS app builds on CI and shows the app name from the shared Kotlin
  code. Nobody on the team has a Mac yet, so it has not run on a device.

Everything else is still to come. The requirements are in
[docs/brief.md](docs/brief.md) and the planned design in
[docs/architecture.md](docs/architecture.md), including the questions that
are still open.

## Install

Not available yet. Test builds will appear on the releases page first, then
in Google Play and the App Store.

## How it works

A Kotlin Multiplatform module holds the data model, the sync and the
schedule logic (overlap checks, week parity, "what is next"), with a local
SQLite database so everything works offline. The Android app and widget are
written with Jetpack Compose and Glance, the iOS app and widget with SwiftUI
and WidgetKit, and both call the shared module directly. A Kotlin server,
shipped as a Docker image the university runs itself, serves the timetable,
holds student accounts, sends push notifications and has the dashboard where
the faculty enters each semester's timetable. Details in
[docs/architecture.md](docs/architecture.md).

## Roadmap

1. Design: screens and colors, agreed before any UI code.
2. Project skeleton and CI for the apps and the server.
3. Shared module: data model, week rules, overlap check, offline store.
4. Server: API, accounts, admin dashboard, Docker image.
5. Android app and widget, then push notifications.
6. iOS app and widget.
7. First release: Android build, server image and deployment notes.

## Contributing

Collaborators work on branches in this repository, everyone else through a
fork. [CONTRIBUTING.md](CONTRIBUTING.md) has the build steps and the pull
request flow, and [AGENTS.md](AGENTS.md) the rules every change follows.

## License

Copyright (c) 2026 Multimedia Entertainment Hub LLC. All rights reserved.
The source is public so it can be read and reviewed, but you may not copy,
change, redistribute or publish the app or its code without written
permission from MEH. See [LICENSE](LICENSE).

The UNSTPB and FAIMA names and logos belong to the university.
