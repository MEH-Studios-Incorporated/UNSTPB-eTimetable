# UNSTPB eTimetable

A timetable app for students of FAIMA (the Faculty of Entrepreneurship,
Business Engineering and Management) at the National University of Science
and Technology POLITEHNICA Bucharest. It shows your classes, where each
professor is and the faculty's announcements, on Android and iOS, and keeps
working offline once the timetable has synced.

Status: planning. There is no app to install yet.

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
- Home screen widgets with today's classes and the next one.
- Native apps: Jetpack Compose on Android, SwiftUI on iOS, with the
  timetable logic shared in Kotlin.

## Why I made it

FAIMA asked Multimedia Entertainment Hub LLC for a timetable app, and I am
building it for MEH. The prototype we were shown had the right screens, but
it was a web page and looked generated.

I want an app that opens instantly, works in a lecture hall with no signal,
and looks like something the faculty would put its name on.

## What works

Nothing yet. The requirements are in [docs/brief.md](docs/brief.md) and the
planned design in [docs/architecture.md](docs/architecture.md), including
the questions that are still open.

## Install

Not available yet. Test builds will appear on the releases page first, then
in Google Play and the App Store.

## How it works

A Kotlin Multiplatform module holds the data model, the sync and the
schedule logic (overlap checks, week parity, "what is next"), with a local
SQLite database so everything works offline. The Android app and widget are
written with Jetpack Compose and Glance, the iOS app and widget with SwiftUI
and WidgetKit, and both call the shared module directly. There is no server
of our own. Details in [docs/architecture.md](docs/architecture.md).

## Roadmap

1. Decide where the timetable data lives and how announcements are pushed.
2. Shared module: data model, sync, offline store, overlap check.
3. Android app and widget.
4. iOS app and widget.
5. A way for the faculty to edit timetables and post announcements.

## Contributing

Collaborators work on branches in this repository, everyone else through a
fork. [CONTRIBUTING.md](CONTRIBUTING.md) has the build steps and the pull
request flow, and [AGENTS.md](AGENTS.md) the rules every change follows.

## License

All rights reserved. The source is public so it can be read and reviewed,
but you may not copy, change, redistribute or publish the app or its code
without written permission. See [LICENSE](LICENSE).

The UNSTPB and FAIMA names and logos belong to the university.
