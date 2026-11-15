# Contributing

Read [AGENTS.md](AGENTS.md) first. It has the rules every change follows,
whether you write it yourself or with a coding agent: commits, writing style,
file headers and how a pull request gets merged.

## Getting access

Collaborators, mostly developers at Multimedia Entertainment Hub LLC (MEH),
are added by the maintainer and push branches to this repository directly.
Anyone else forks it and opens a pull request from the fork; the flow is the
same.

## Building

You need:

- JDK 17 or newer to start Gradle. The build itself uses JDK 21, which
  Gradle downloads when the machine does not have it. Android Studio's
  bundled JDK works (`export JAVA_HOME=/opt/android-studio/jbr` on Arch).
- The Android SDK with platform 37 (`ANDROID_HOME`, or `sdk.dir` in an
  ignored `local.properties`). Android Studio installs it on first start.
- For the server image: Docker with the compose plugin.
- For iOS: a Mac with the current Xcode and XcodeGen
  (`brew install xcodegen`). Without a Mac you can still work on everything
  else; CI builds the iOS app on a macOS runner.

The checks CI runs on Linux:

```sh
./gradlew ktlintCheck :shared:verifySqlDelightMigration :shared:allTests :server:test :androidApp:lintDebug :androidApp:assembleDebug
tools/check-writing.sh
shellcheck tools/*.sh
```

`./gradlew ktlintFormat` fixes most style problems by itself.

Versions live in `gradle/libs.versions.toml`. The Android Gradle Plugin
must stay at a version the current stable Android Studio supports, or
Studio cannot sync the project: check the compatibility table at
https://developer.android.com/build/releases/gradle-plugin before raising
it. Decline Studio's offers to upgrade versions; change them in a pull
request instead.

Running things:

```sh
# Android: install on the running emulator or a connected phone
./gradlew :androidApp:installDebug

# Development server on http://localhost:8080, reachable from emulators and
# USB phones. It runs its own PostgreSQL (data in server/build/dev-postgres;
# delete that folder to start over, with the server stopped) and fills an
# empty database with the sample semester from SampleData. The dashboard is
# at http://localhost:8080/admin, admin@etimetable.test / parola-dev.
tools/dev-server.sh

# FAIMA's real timetable instead of the sample one: downloads the faculty's
# workbooks from Google Sheets and converts them; the dev server imports the
# result on its next start. Sheet ids as arguments or one per line in the
# ignored tools/faima-timetable/sheets.local. Needs python3 and curl.
tools/faima-timetable/update.sh

# A second dev server next to the first needs its own port, folder and
# PostgreSQL port, or the two share and break one database:
ETT_PORT=18080 ETT_DEV_DB_DIR=build/dev-postgres-2 ETT_DEV_DB_PORT=54330 ./gradlew :server:runDev

# Server and PostgreSQL in Docker
cp deploy/.env.example deploy/.env
./gradlew :server:installDist
docker compose -f deploy/docker-compose.yml up --build

# iOS (macOS)
cd iosApp && xcodegen generate && open iosApp.xcodeproj
```

Push notifications need a Firebase project. Without one everything builds
and runs, with no push. To try them, add the Android app's values from the
Firebase console (Project settings, Your apps) to the ignored
`~/.gradle/gradle.properties`:

```properties
firebaseAppId=1:123456789012:android:0123456789abcdef
firebaseApiKey=AIza...
firebaseProjectId=your-project
```

and start the dev server with the project's service account key:
`ETT_FCM_SERVICE_ACCOUNT="$(jq -c . key.json)" tools/dev-server.sh`.
Publish an announcement in the dashboard and it arrives on the emulator,
which needs a Google Play system image. Your computer's clock has to be
synchronized (`timedatectl set-ntp true`); otherwise Google answers `400`
and the dashboard says the clock is off.

The app's first screen says whether it reaches the server. Debug builds
talk to `127.0.0.1:8080` on the device, which `tools/dev-server.sh` forwards
to your machine with `adb reverse`. Use it rather than `./gradlew
:server:run` alone: the emulator's Wi-Fi network cannot reach the host at
`10.0.2.2`, and a phone on USB cannot reach your machine at all without
the forward. A different
server address is passed as `-PapiBaseUrl=https://...`; release versions
come from the tag as `-PappVersion=1.2.3`.

### On Windows

Everything except iOS builds on Windows. Use `gradlew.bat` for `./gradlew`,
and the PowerShell versions of the two scripts that deal with local paths:

```powershell
tools\dev-server.ps1                     # tools/dev-server.sh
tools\faima-timetable\update.ps1 [ID ...] # tools/faima-timetable/update.sh
```

If PowerShell refuses to run them, start it with
`powershell -ExecutionPolicy Bypass -File tools\dev-server.ps1`. The SDK is
found through `ANDROID_HOME`, `local.properties` or
`%LOCALAPPDATA%\Android\Sdk`; the Python step needs Python 3 (`py` or
`python` on the path). Environment variables go before the script with
`$env:`, for example `$env:ETT_DEV_DB_DIR = 'build/dev-postgres-2'`.

`tools/check-writing.sh` and `tools/release-notes.sh` are what CI runs, so
they have no second version that could drift from them. Run them from Git
Bash, which comes with Git for Windows: `sh tools/check-writing.sh`.
`.gitattributes` keeps the shell scripts and `gradlew` with LF line endings
on a Windows checkout.

## Checking a change by hand

With `tools/dev-server.sh` running and the debug app installed, these are
the checks worth repeating when a change touches the area. Do them in
Romanian and in English.

- Gestionare orar in the dashboard: search by subject, professor, room and
  group (a group also finds its series courses). Filter by a weekday and
  by classes without a room or professor, then clear the filters. Select
  classes, change the room or shift by 30 minutes, check the preview,
  cancel once and check nothing changed, then confirm and sync the phone.
  A preview confirmed after another tab saved an edit must ask for a
  reload. A shift past midnight is refused; a new double booking shows a
  warning before confirming.
- Professors tab: search by name typed without diacritics, by email and
  by subject. A professor's page shows the office, email or the missing
  labels, office hours and the week. The "now" filter can be empty outside
  teaching hours. With the network off after a sync, everything still
  opens.
- Announcements: publish a post with bold text, a list and an https link,
  sync, open it and check the formatting and that it is marked read.
  Search, the unread filter and "Mark all as read". Pin one and check it
  sits on top. Delete one and check it goes from the phone. With more than
  20 posts, page through the dashboard list and edit an older post.
- Timetable reports: report a class from its details, see it in the
  dashboard, try a second report on the same class (refused), then mark it
  fixed or dismiss it. Offline, the form stays filled and says the report
  was not sent.

## Reporting a problem

Use the bug report form in the issues. Give the app version, the phone and
OS version, whether you are a national or Erasmus student and your group or
the subject involved, and a screenshot of what looks wrong.

## Pull requests

- Branch from an up-to-date `main`, one topic per pull request.
- Fill in the template: what changed, and what you actually tested.
- CI must pass. Pull requests are squash merged, so the branch history does
  not matter.
- Data model, sync format and design token changes wait for a review from
  the maintainer.

## Code style

- Every source file starts with the copyright header from `AGENTS.md`
  section 5, naming Multimedia Entertainment Hub LLC.
- Kotlin follows the official Kotlin style, Swift the Swift API design
  guidelines. Linters are added to CI together with the first code.
- Comments say what is not obvious from the code, briefly. No change logs
  in comments; git has the history.
- Text in the app, the docs and commit messages follows the writing rules in
  `AGENTS.md` section 4.

## License of contributions

The project is owned by Multimedia Entertainment Hub LLC and all rights are
reserved (see [LICENSE](LICENSE)).

- MEH developers: your work here is covered by your agreement with MEH.
- Anyone else: by sending a contribution you transfer its copyright to MEH.
  Where the law does not allow that, you give MEH a permanent, worldwide,
  royalty-free right to use, change, distribute and relicense it, with no
  obligation to name you.
