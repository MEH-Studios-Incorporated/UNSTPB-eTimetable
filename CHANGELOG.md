# Changelog

Each section is also the text of the matching GitHub release.

## Unreleased

### Added

- Android app: sign in or create an account, then choose a group and
  half-group, or, for Erasmus students, the course and lab slots to attend.
  A slot that overlaps one already picked is refused and the clash named.
- Today and week views of the timetable, with the week number and parity,
  the next class, a line at the current time and the details of each class.
- Announcements from the faculty, newest first, pinned ones on top, unread
  ones marked.
- The timetable works offline after the first update and refreshes every
  few hours; times are always Bucharest time, whatever the phone's time zone.
- Creating an account asks for first name, last name and phone number, and
  the password twice. "Remember me" keeps you signed in; without it the
  session ends when the app is closed.
- Announcements open in full with their formatting (lists, bold, links).
- Swiping between days, transitions between screens, and the About page
  with links to mehstudios.net and support@mehstudios.net.
- The current teaching week and whether it is odd or even, always visible
  at the top ("Săpt. 5, impară"), or "Vacanță" during a break.
- Romanian and English inside the app, chosen in the profile or on the
  sign-in screen, whatever the phone's language.
- Light or dark theme in the profile, or the phone's setting (the default).
- Three timetable layouts to choose from in the profile: Timeline, List
  (cards with the breaks between classes, the week as an agenda) and Table
  (dense rows, the week as a table of times and days like the faculty's
  sheet), and whether the timetable opens on the day or the week.
- A page for each professor: where they are now, their office hours and
  their classes this week. Tapping a name on a class opens it.
- Labs and seminars name their assistant and the professor of the course.
- The app icon is the FAIMA seal.
- A Subjects tab: every subject, searchable, starting with your programme
  and year. A subject's page lists its course, seminar, lab and project
  slots with room, groups and teacher, and can hide the ones that overlap
  your timetable. Add another group's lab once (for a recovery) or for the
  whole semester; Erasmus students add and remove their picks here. A slot
  that overlaps your timetable is refused and the clash named. Tapping a
  subject's name on a class opens its page.
- A public page at the server's address for students: what the app does,
  download links set by the deployment, real screenshots, the current
  teaching week, in Romanian and English, with a link to the dashboard.
- Dashboard overview: today's teaching week, the semester's numbers, student
  accounts, and a list of what still needs doing (activities without a
  teacher or a room, double bookings, professors without an email).
- Announcements can be previewed before publishing and edited afterwards.

### Changed

- The dashboard has a sidebar, page headers with their actions, groups
  listed by series and a cleaner look throughout.

### Changed

- The current week sits in a quieter badge: its number in a grey block,
  "Săptămâna impară" beside it.
- The profile is a menu: settings (language, theme, timetable layout and
  the view it opens on) and the about page each have their own page, and
  every choice opens a list of options.
- Pages opened over a tab have a round back button and their name at the
  top, which stay in place while the page scrolls.
- Changing the language fades the screen out and back in instead of
  redrawing it at once.

### Fixed

- In the Romanian week view the "Azi" button was squeezed into a column of
  letters; the week arrows now sit next to the dates.
- One-hour classes no longer cut their text off: the card shows the name
  and one line with time, type and room.
- Room codes are written the same everywhere, without a space after the
  building ("BN212b", "L201 Polizu"), also when an admin imports them with
  spaces.
