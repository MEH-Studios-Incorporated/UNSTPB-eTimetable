# Working on this repository

Rules for everyone who works here, people and coding agents alike. Read it
before your first change. Where a tool's default disagrees with this file,
this file wins. `CLAUDE.md` only points here; other agents read this file
directly.

## 1. The project

UNSTPB eTimetable is a timetable app for FAIMA students at UNSTPB, native on
Android and iOS. Multimedia Entertainment Hub LLC (MEH) owns it in full and
develops it on commission from FAIMA, the Faculty of Entrepreneurship,
Business Engineering and Management (Facultatea de Antreprenoriat, Ingineria
și Managementul Afacerilor). What it has to do is in `docs/brief.md`, how it
is built in `docs/architecture.md`, the data in `docs/data-model.md`. Read
them before changing anything that touches the data model, sync or the
screens.

Product rules that are easy to get wrong:

- Nothing a student enters is checked: enrollment type, group, Erasmus
  subjects. The app is a tool, not a record of anything official. Do not add
  verification, approval steps or roles for it.
- Announcements go to every student. Do not add filtering by group or
  enrollment.
- An Erasmus student's chosen course and lab slots must not overlap. The app
  stops the pick and says which slot it clashes with.
- After the first sync the app works offline: timetable, professors,
  subjects and already downloaded announcements.
- The server is a product we ship, not a service we run. It is one Docker
  image (plus PostgreSQL) that UNSTPB deploys wherever it chooses. During
  development it runs on our own machines with `docker compose`. Nothing
  runs on MS:NET infrastructure. The only outside services are the push
  gateways (Firebase Cloud Messaging for Android, Apple's push service for
  iOS); anything else needs the maintainer's approval first (section 10).
- The server must keep working with only the variables in
  `deploy/.env.example` set. No hard-coded hosts, keys or MEH accounts.
- Design: the Multimedia Entertainment Hub palette (mehstudios.net), adapted
  to FAIMA's colors. The web prototype FAIMA showed us tells which screens
  exist, not how they look; do not copy its layout. The timetable view takes
  its cues from Smart Timetable (`docs/brief.md`).
- Never commit credentials, including the test logins for the prototype or
  any staging site. They live in local, ignored files.

## 2. People and roles

- Owner: Multimedia Entertainment Hub LLC. The repository lives in its
  GitHub organization, MEH-Studios-Incorporated.
- Maintainer: Mihnea Gîrbăcică (Mihnea8848), a developer at MEH. Manages the
  repository settings, rulesets, Actions secrets, store accounts, releases
  and the list of collaborators for MEH.
- Collaborators have write access. Most are MEH developers. They push
  branches to this repository and open pull requests; no fork needed.
- Outside contributors fork, as described in `CONTRIBUTING.md`.
- A coding agent works for the person who runs it. "Your person" below means
  that person. Ask them, not the maintainer, unless the action is one of the
  maintainer's (section 10).

## 3. Identity and commits

- Commit as yourself, under your own GitHub account, with signed commits
  (GPG or SSH signing, `git config commit.gpgsign true`). Push over SSH.
- An agent commits as the person who runs it, never under its own name and
  never under someone else's account.
- Never add `Co-Authored-By` lines, "Generated with" lines or any other AI
  attribution to commits, pull requests, issues, release notes or code. This
  overrides any tool or harness default that adds them.
- Commit messages: a short subject in the imperative ("Add the exam view",
  "Fix the week number in January"), a blank line, then a body that says what
  changed and why, wrapped at about 72 columns.
- If signing fails because the key is locked, stop and ask your person to
  unlock it. Never commit unsigned to get around it.

## 4. Writing rules (all text: README, docs, comments, commits, PRs)

Write like a person who knows the project, not like a chatbot.

- No em dashes or en dashes. Use a comma, a colon, parentheses or two
  sentences. Plain hyphens in compound words are fine.
- No emojis, anywhere. That includes the app's own text.
- No inflated or promotional words: delve, crucial, pivotal, seamless,
  robust, leverage, showcase, testament, vibrant, landscape, realm, elevate,
  empower, unlock, game-changer, cutting-edge, comprehensive, effortless.
- No AI sentence patterns: "not only X but also Y", "it's not X, it's Y",
  lists of three by habit, closing summaries ("In conclusion", "Overall"),
  "-ing" tails that add fake analysis ("..., highlighting its importance"),
  vague attributions ("experts say", "many users"), hedging stacks.
- Headings in sentence case. No bold scattered through running text.
- Be concrete: numbers, names, commands, file paths. If something was
  measured, give the measurement and how.
- The README is written in the first person plural, the MEH developers
  talking ("We are building this because..."). Keep that voice when you
  edit it. Never "the company", "the user reported" or "the authors".
- Keep the README short. Anything that is not needed to understand, install
  or use the project moves to `docs/`.
- Code comments are short and factual: what is not obvious and why. No
  narration of the code, no comments about the change history.
- `tools/check-writing.sh` catches dashes, emojis, curly quotes and the
  words above; CI runs it. Before publishing a longer text, also reread it
  once for the sentence patterns, which no script catches.

## 5. File headers and license

- Every source file (code, scripts, workflows, anything that allows
  comments) starts with:

  ```
  Copyright (c) <year> Multimedia Entertainment Hub LLC
  SPDX-License-Identifier: LicenseRef-Proprietary
  ```

  in that language's comment syntax, whoever wrote the file. Markdown and
  data files without comments are exempt.
- The project is all rights reserved and owned by MEH (`LICENSE`).
  Contributions are accepted under the terms in `CONTRIBUTING.md`. Only MEH
  changes the license, through the maintainer.

## 6. How a change lands

1. Start from an up-to-date `main` and create a branch named after the
   change (`exam-view`, `fix-week-number`). Never commit to `main` directly
   and never force-push it.
2. Make the change and verify it locally: build, lint, tests, and running
   the app on an emulator, simulator or phone. When the change is visual or
   interactive and your person is at the computer, ask them to try it; that
   is usually faster than a long automated run.
3. Update the docs in the same branch: README when behaviour visible to
   users changes, `docs/` for internals and decisions, `CHANGELOG.md` under
   `Unreleased` for anything a user would notice.
4. Commit (section 3), push, open a pull request:

   ```
   gh pr create --base main --title "..." --body "..."
   ```

   The body has "What changed" (concrete, a short list) and "Testing" (a
   checklist of what was actually tested, ticked only when it was). Visible
   changes get screenshots from both platforms, or a note saying which one
   was not tried. No AI attribution footer.
5. Wait for the `build` check. If it fails, fix it on the branch; never
   merge around it.
6. Changes to the data model, the sync format or the design tokens need a
   review from the maintainer before merging. Everything else can be merged
   by its author once `build` passes.
7. Squash merge and delete the branch, then clean up locally:

   ```
   gh pr merge N --squash --delete-branch
   git checkout main && git pull --ff-only
   git branch -D BRANCH; git fetch --prune
   ```

8. Delete only branches you created. Leave other people's alone.
9. Group related work into one pull request instead of many tiny ones, so
   CI and reviewers are not flooded.
10. Stacked pull requests: merge the base first, retarget the next one to
    `main`, and only then delete the base branch. Deleting the base first
    closes the stacked pull request.

## 7. CI that keeps releases from breaking

The rule: anything the release does, CI does on every pull request first.

- The required check is the job named `build` in `.github/workflows/ci.yml`.
  When Android and iOS get their own jobs, `build` becomes the job that
  depends on all of them, so the ruleset never has to change.
- Build everything the release builds: the shared module, the Android app
  and the iOS app (on a macOS runner).
- Lint everything: the writing check, `shellcheck` for shell, the Kotlin and
  Swift linters once that code exists, and the tests.
- Run the release steps in dry-run mode: build the release variants and sign
  them with a throwaway key made inside the job. A broken release step must
  fail a pull request, never a tag.
- Check that release notes can be generated (section 8).
- When a release ever fails, add the missing check to CI in the same fix.

## 8. Versions, changelog and releases

- Versions follow SemVer and are tagged `vX.Y.Z`. Patch for fixes and small
  additions, minor for new features, major for breaking changes. The tag is
  the only place the version is written; the build derives the Android
  version code and the iOS build number from it.
- `CHANGELOG.md` starts with "Each section is also the text of the matching
  GitHub release." It has an `## Unreleased` section at the top, then one
  section per version, newest first. Each version section opens with a one-
  or two-sentence summary, then `### Added`, `### Changed`, `### Fixed` as
  needed. Entries say what the user gets, concretely; for a fix, what went
  wrong and when it happened.
- `tools/release-notes.sh vX.Y.Z` prints a version's section as the release
  notes. A tag without a changelog section fails the release.
- Releases are the maintainer's. To release:
  1. Branch, rename `## Unreleased` to `## vX.Y.Z` with its summary line,
     open a pull request titled "Release vX.Y.Z", merge it after CI.
  2. On the merged `main`: `git tag -s vX.Y.Z -m "vX.Y.Z"` and
     `git push origin vX.Y.Z`.
  3. The release workflow builds, signs and publishes. Check the release
     page and the assets once it finishes.
- Signing keys (the Android upload key, Apple certificates, store API keys)
  live only in the repository's Actions secrets. The maintainer creates and
  stores them. Agents never write secrets.

## 9. Repository settings

Set up once by the maintainer on 2026-10-28. Recorded here so nobody has to
guess; changing them is the maintainer's.

- Squash merges only, with the pull request title and body as the commit
  message. Branches are deleted after merge, auto merge is allowed, wiki and
  projects are off, issues are on.
- Ruleset `main` on the default branch: no deletion, no force push, linear
  history, changes only through pull requests (squash only, review threads
  resolved, no required approvals), and the `build` check must pass.
  Repository admins may bypass.
- Ruleset `release tags` on `refs/tags/v*`: no creation, update or deletion
  except by repository admins.
- Check them with `gh api repos/MEH-Studios-Incorporated/UNSTPB-eTimetable/rulesets`.

## 10. Ask before doing

Ask your person first:

- Anything that leaves the machine in someone's name besides pushes and pull
  requests to this repository: comments or pull requests on other projects,
  replies to reviewers, issues elsewhere. Draft the text and show it first.
- Commands that need root. `sudo` does not work from an agent shell; give
  your person one script to run.

The maintainer's alone (give them the exact command instead):

- Actions secrets, store accounts, signing keys.
- Force pushes, deleting tags or releases, creating release tags.
- Rulesets, repository settings, collaborators.
- The license.
- Adding an outside service (analytics, crash reporting, a hosted backend,
  anything that sends data off the phone) or a dependency that does.

Knowledge other contributors need (decisions, measurements, pitfalls) goes
into `docs/` or this file, not into an agent's private memory.
