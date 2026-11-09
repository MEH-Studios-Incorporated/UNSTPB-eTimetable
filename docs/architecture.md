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
| Server database | PostgreSQL through JDBC and HikariCP, Flyway for migrations |
| Admin dashboard | HTML rendered by the server (kotlinx.html), plain forms, no JavaScript |
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

## Server storage

A semester and everything in it (groups, subjects, rooms, activities and
so on) is stored as rows of JSON documents in the shared model's format,
one row per item, with a version number that changes on every edit. The
dataset endpoint reads them, builds the response once per version and
answers `304 Not Modified` while the version stays the same. One faculty has
a few thousand rows at most and the app always reads a semester whole, so
this keeps the server code small; plain JDBC is enough for the handful of
queries, without an ORM.

Students are a separate table, with Argon2id password hashes and session
tokens of which only a SHA-256 is stored.

Timetable problem reports are stored separately from the semester dataset.
They keep the student's description and a server-generated snapshot of the
class, so a later timetable edit or import does not change what was
reported. The dashboard shows the student's name and email so staff can
follow up. Reports are available only to dashboard administrators and
editors, and are deleted with the student's account. They do not enter the
dataset or its offline cache.

Submitting a report requires a student session and a connection. The server
accepts at most five reports per account in a rolling hour and one open
report per class and semester. It locks the student row while checking the
limit and inserting, so simultaneous requests, multiple sessions and server
restarts do not bypass the limit. Duplicate or invalid reports do not use
the allowance. A rate-limited response includes `Retry-After` in seconds.

## Who runs the server

We ship the server; we do not host it. UNSTPB decides where it runs and
starts it with `deploy/docker-compose.yml`. Everything it needs comes from
environment variables listed in `deploy/.env.example`: database, public URL,
admin bootstrap account, push credentials. During development each of us
runs it locally with the same compose file. Nothing runs on MS:NET
infrastructure.

The Gradle build runs outside the image (`./gradlew :server:installDist`),
because configuring the project also configures the Android modules, which
need the Android SDK. `server/Dockerfile` only copies the result onto a
distroless Java 21 base.

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
  The cache keeps 100 recent posts and up to 100 pinned posts fetched
  separately, so older pinned notices are not lost at the recent-post limit.
- The student's profile is stored on the server and cached on the phone, so
  edits made offline are sent on the next sync.

The app syncs when opened, after a push, and periodically in the background
(WorkManager on Android, background refresh on iOS).

## Accounts

Students sign up with an email address and a password. Nothing is verified
beyond that: enrollment, group and Erasmus picks are whatever the student
chooses. Passwords are hashed with Argon2id, and the app keeps a session
token. Admin accounts are separate, with two roles: admin (everything) and
editor (timetables and announcements). If the deployment sets SMTP
variables, students can reset a forgotten password by email; otherwise an
admin resets it from the dashboard.

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

What is built so far:

- The server sends to Android only. `ETT_FCM_SERVICE_ACCOUNT` holds the
  service account key file's JSON on one line; without it push is off and
  the log says so at start. The server signs its own OAuth token with the
  key (`server/.../push/GoogleAccessTokens.kt`), so it needs no Google
  library. Tokens registered by iPhones are kept and skipped until APNs is
  added together with the iOS app.
- Messages carry only data (`kind`, `id`, `title` and the first 180
  characters of the text without Markdown, `PushFields` in `shared/`). The
  app builds the notification itself, so tapping it opens the announcement,
  and starts a sync so the announcement is on the phone. While the main
  screen is visible the app shows its own bar instead (`InAppAlerts`, which
  knows the screen is visible because only it collects the alerts).
- Sending runs in the background after the dashboard publishes, 8 messages
  at a time. Editing an announcement sends nothing.
- Google refuses the server's OAuth token when the server's clock is more
  than a few minutes off. Before sending, the server reads Google's time
  from the `Date` header of a HEAD request to the token endpoint
  (`server/.../push/ClockCheck.kt`, checked at most once a minute). If the
  two differ by more than 5 minutes, the announcement is still published
  but nothing is sent, and the dashboard says how far the clock is off.
  It is not sent later either; a way to send it again is still to do.
- The app has no `google-services.json`. It sets Firebase up from three
  Gradle properties (`firebaseAppId`, `firebaseApiKey`,
  `firebaseProjectId`), so CI and anyone without them still build an app
  that works, just without push.
- A token is tied to the session that registered it last
  (`push_token.session_token_hash`, deleted with the session). Signing
  out ends the session and so removes the token. A phone that signs out
  offline keeps the old session token (`SessionStore.endedSession`) and
  ends it with `POST /auth/logout` on its next sync; until then the server
  still sends, and the app drops what arrives. A session without "remember
  me" is ended the same way when the app opens after being closed; a
  push that starts the app in the background before that is dropped. An
  admin setting a new password ends every session and removes their tokens
  too.
- One student keeps at most 10 tokens; registering an eleventh drops the
  one updated longest ago. Tokens are at most 1024 characters (FCM's are
  about 160). `PUT /me/push-tokens` allows 10 requests per student an hour;
  the app sends its token only when Firebase changes it. The request log
  shows these paths as `/me/push-tokens/<token>` (`loggedPath` in
  `Application.kt`), so no token is written to the logs.
- FCM answers that mean the token will never work (`UNREGISTERED` or
  `SENDER_ID_MISMATCH` in `error.details`, or `INVALID_ARGUMENT` naming
  the registration token) delete it. `429` and `5xx` are retried twice,
  after `Retry-After` when FCM sends one (at most 30 s), otherwise after 1 s
  and 2 s. When Google refuses the service account, the run stops with one
  log line and Google is not asked again for a minute.
- Announcement titles and the first 180 characters pass through Google's
  servers. The privacy text in the app says so.

## Admin dashboard

At the start of each semester an admin enters or imports:

- the semester calendar (start, end, holidays, which weeks are odd or even);
- programmes, years, series, groups and half-groups;
- subjects, professors with office hours, buildings and rooms;
- activities, on a week grid per group, with a CSV import for bulk entry.

Editors post announcements there. The dashboard checks entries with the
same rules as the app (for example, a room or professor booked twice at the
same time).

The reports section shows open reports first and has a separate closed
list. Staff can open the existing activity editor from a report, then mark
it fixed after saving the correction, or dismiss it when no correction is
needed. Closing a report does not edit the timetable. These actions use
the dashboard's existing session and same-origin form protection.

A tool in `tools/` that reads FAIMA's published timetable into the CSV
format is for development only, to get realistic data.

## Open questions

- Which store accounts publish the apps (MEH's or the university's)? That
  decides who owns the push credentials.
- Does the dashboard need sign-in through the university's Microsoft
  accounts later, or are local admin accounts enough?
