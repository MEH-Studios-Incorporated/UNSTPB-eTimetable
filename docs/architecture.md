# Architecture

How the project is built. Update it in the same pull request when a
decision changes.

## Parts

```
androidApp/   Android app and widget (Kotlin, Jetpack Compose, Glance)
iosApp/       iOS app and widget (Swift, SwiftUI, WidgetKit)
shared/       Kotlin Multiplatform: data model, schedule logic, sync, local
              database; built for Android, iOS and the JVM
server/       Ktor server: public API, accounts, push, admin dashboard
deploy/       docker-compose.yml and .env.example for running the server
```

One Gradle build covers everything except the Xcode project, which links the
framework `shared/` produces.

## Stack

| Part | Choice |
| --- | --- |
| Shared logic | Kotlin Multiplatform, kotlinx.serialization, kotlinx.datetime |
| App storage | SQLite through SQLDelight |
| App networking | Ktor client |
| Android UI | Jetpack Compose with Material 3, themed with our own tokens |
| Android widget | Jetpack Glance |
| iOS UI | SwiftUI |
| iOS widget | WidgetKit, reading the app's database through an App Group |
| Server | Ktor on the JVM (Java 21) |
| Server database | PostgreSQL, Exposed for queries, Flyway for migrations |
| Admin dashboard | HTML rendered by the server (kotlinx.html) with htmx |
| Push | Firebase Cloud Messaging HTTP v1 for Android, APNs for iOS |
| Packaging | One Docker image for the server, `docker compose` with PostgreSQL |

Why:

- Both apps use their platform's own UI toolkit, so they look, scroll and
  behave like other apps on the phone. There is no web view and no
  JavaScript bridge, unlike Electron, Capacitor or React Native, and no
  separate rendering engine, unlike Flutter.
- The rules that must give the same answer everywhere (odd and even weeks,
  holidays, "what is next", slot overlaps) are written once in `shared/`.
  The apps use them offline, the server uses the same code to validate what
  admins enter.
- The dashboard is part of the server, so the university deploys one image
  and nothing else. Rendering it on the server keeps it fast on any machine
  and avoids a second build chain.
- Widgets are native on both platforms and read the same database as the
  app, so they work offline too.

## Who runs the server

We ship the server; we do not host it. UNSTPB decides where it runs and
starts it with `deploy/docker-compose.yml`. Everything it needs comes from
environment variables listed in `deploy/.env.example`: database, public URL,
admin bootstrap account, push credentials. During development each of us
runs it locally with the same compose file. Nothing runs on MS:NET
infrastructure.

Release builds publish the image as
`ghcr.io/meh-studios-incorporated/unstpb-etimetable-server:vX.Y.Z`.

## Sync and offline use

The app keeps everything it shows in SQLite:

- The semester dataset (calendar, groups, subjects, professors, rooms,
  activities) comes from one endpoint, versioned with an `ETag`. An
  unchanged timetable costs one request answered with `304 Not Modified`.
  A changed one is downloaded whole; for one faculty that is a few hundred
  kilobytes, compressed.
- Announcements are paged, newest first, and kept after download.
- The student's profile is stored on the server and cached on the phone, so
  edits made offline are sent on the next sync.

The app syncs when opened, after a push, and periodically in the background
(WorkManager on Android, background refresh on iOS).

## Accounts

Students sign up with an email address and a password. Nothing is verified
beyond that: enrollment, group and Erasmus picks are whatever the student
chooses. Passwords are hashed with Argon2id, and the app keeps a session
token. Admin accounts are separate, with two roles: admin (everything) and
editor (timetables and announcements).

## Push notifications

When an admin publishes an announcement, the server sends it to every
registered device:

- Android: Firebase Cloud Messaging, HTTP v1 API. The app includes only the
  `firebase-messaging` library. In practice FCM is the only reliable way to
  wake an Android app in the background.
- iOS: Apple's push service (APNs) directly, with token authentication. No
  Firebase code in the iOS app.

Devices register their push token with the server after sign-in. A failed
token (app uninstalled) is removed on the first error. The credentials (a
Firebase service account and an APNs key) belong to whoever publishes the
apps in the stores; the server reads them from the environment.

## Admin dashboard

At the start of each semester an admin enters or imports:

- the semester calendar (start, end, holidays, which weeks are odd or even);
- programmes, years, series, groups and half-groups;
- subjects, professors with office hours, buildings and rooms;
- activities, on a week grid per group, with a CSV import for bulk entry.

Editors post announcements there. The dashboard checks entries with the
same rules as the app (for example, a room or professor booked twice at the
same time).

A tool in `tools/` that reads FAIMA's published timetable into the CSV
format is for development only, to get realistic data.

## Open questions

- Which store accounts publish the apps (MEH's or the university's)? That
  decides who owns the push credentials.
- Does the dashboard need sign-in through the university's Microsoft
  accounts later, or are local admin accounts enough?
