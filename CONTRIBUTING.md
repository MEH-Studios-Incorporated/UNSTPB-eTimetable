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

There is no app code yet. Once there is, this section lists the exact
commands. The plan, so you can set up early:

- Shared module and Android: JDK 21 and Android Studio (or the Android SDK
  command line tools). Builds run through the Gradle wrapper in the
  repository.
- iOS: a Mac with the current Xcode. Without a Mac you can still work on the
  shared module and the Android app; CI builds iOS on a macOS runner.

The checks CI runs today work on any Linux or macOS machine:

```sh
tools/check-writing.sh
shellcheck tools/*.sh
```

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
