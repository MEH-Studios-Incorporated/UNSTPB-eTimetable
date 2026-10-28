# Brief

What FAIMA asked for, written down so every contributor works from the same
list. Multimedia Entertainment Hub LLC builds the app on commission.

## Students and profiles

Every student has a profile in the app and picks their enrollment type:

- National students pick their group. Their timetable is the one the
  faculty publishes for that group, filled in by the faculty (imported
  automatically or entered through an admin page).
- Erasmus students pick their subjects from their learning agreement. They
  can take subjects from several specializations and years, so there is no
  group timetable for them.

Nobody checks these choices. Each student is responsible for the group or
subjects they pick. The app is a tool for finding your classes, not a
system for grades or attendance, and it needs no security beyond keeping
the faculty's admin side closed.

## Screens

- Timetable: the student's week and day, in the spirit of Smart Timetable
  (https://play.google.com/store/apps/details?id=app.smart.timetables).
- Subject: every course and lab slot of one subject, with day, hours,
  professor, building and room. Students use it to attend another group's
  lab or to recover a missed one. Erasmus students use it to pick their
  slots when no lab is set aside for Erasmus; the app refuses a slot that
  overlaps one already picked.
- Professors: where a professor is and what they are doing (the class they
  teach, the building and room, office hours).
- Announcements: posts from the faculty, the same for every student, with
  push notifications.
- Profile and settings: enrollment type, group or subjects, notifications.

FAIMA showed us a web prototype. It shows which screens are expected, but its
layout is not followed. The link and its test login are kept outside the
repository.

## Platforms and behaviour

- Android and iOS, native. Desktop may come later.
- Home screen widgets with the timetable.
- Works offline after the timetable has synced.
- Light on battery, storage and data; responsive on phones and tablets.

## Design

The look starts from the Multimedia Entertainment Hub palette
(https://mehstudios.net) and adapts it to FAIMA's own colors. It has to look
designed by people: no generic gradients, glowing cards or emoji, and no
template layouts.

## Hosting

No server of our own, and nothing on MS:NET infrastructure. GitHub can be
used where it helps. See [architecture.md](architecture.md) for what that
means for data and notifications.
