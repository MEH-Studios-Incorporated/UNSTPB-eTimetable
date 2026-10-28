# Architecture

The plan before any code exists. Update it when a decision changes.

## Stack

| Part | Choice |
| --- | --- |
| Shared logic | Kotlin Multiplatform module `shared/` |
| Local storage | SQLite through SQLDelight, in `shared/` |
| Networking | Ktor client, in `shared/` |
| Android app | Kotlin, Jetpack Compose, `androidApp/` |
| Android widget | Jetpack Glance |
| iOS app | Swift, SwiftUI, `iosApp/` |
| iOS widget | WidgetKit extension, sharing the database through an App Group |

Why this one:

- Both apps use their platform's own UI toolkit, so they look, scroll and
  behave like other apps on the phone. There is no web view and no
  JavaScript bridge, unlike Electron, Capacitor or React Native, and no
  separate rendering engine, unlike Flutter.
- The parts that must give the same answer on both platforms (sync, the
  offline store, overlap checks, odd and even weeks, "what is next") are
  written once in Kotlin. On iOS they compile to a native framework that
  Swift calls directly.
- Widgets are native on both sides and read the same database as the app,
  so they work offline too.
- It leaves room to move: Compose Multiplatform can share individual
  screens later if that saves work, and a desktop app can reuse `shared/` on
  the JVM.

## Data and sync (open)

There is no server of our own. The app keeps a full copy of the data it
needs in SQLite and refreshes it when online, using HTTP caching (`ETag`)
so an unchanged timetable costs one small request.

Option A, GitHub only:

- Timetables, subjects, rooms, professors and announcements are JSON files
  in a data repository, published on GitHub Pages.
- The faculty edits them through a small admin page on GitHub Pages that
  commits through the GitHub API, or through pull requests.
- Profiles (enrollment, group, Erasmus subjects) stay on the phone. There
  are no server accounts.

Option B, a hosted backend service (Firebase or Supabase, free tier):

- Real accounts, so a profile follows a student to a new phone.
- Data lives in their database, edited from an admin page.
- Adds an outside service and its limits, which needs the maintainer's
  approval (`AGENTS.md` section 10).

## Push notifications (open)

Phones only receive pushes through Firebase Cloud Messaging (Android) and
Apple's push service (iOS); FCM can deliver to both. Something has to send
the message when an announcement is posted. With option A that is a GitHub
Actions workflow that runs when an announcement file is added and calls FCM
with a key kept in the Actions secrets. The app subscribes to one topic, as
announcements are the same for everyone.

## Open questions

- Do students need real accounts (option B), or is a profile stored on the
  phone enough (option A)?
- Is Firebase Cloud Messaging acceptable for push? The alternative is a
  workflow that calls Apple's push service directly for iOS, while Android
  checks for new announcements periodically.
- In what form does FAIMA have the timetable today (spreadsheet, PDF, a
  university system)? That decides how the import works.
- Which languages does the app need? Romanian and English are assumed, for
  the Erasmus students.
