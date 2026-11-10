# Changelog

Each section is also the text of the matching GitHub release.

## Unreleased

### Added

- A "Săptămână" view in Gestionare orar: the week of a group, half-group,
  room or professor on an hour grid, each class a block in its subject's
  colour with its type, room and time. Classes at the same time sit side by
  side, and Toate / Impare / Pare fades the classes of the other weeks.
  Clicking a class opens its form beside the grid with any clash in the
  same room, with the same professor or the same students; saving returns
  to the same week with the class still selected.
- The group, room and professor pages show their week on the same hour
  grid instead of five lists, with office hours as grey blocks.

- Two-step sign-in for the dashboard with an authenticator app (a QR code
  to scan, a six-digit code after the password) and ten one-time recovery
  codes for a lost phone.
- "Contul meu" in the dashboard: change the password (the current one is
  asked for), see and close open sessions, and see recent sign-ins,
  including failed ones.
- Administrators can change another account's role, give it a new
  temporary password, close its sessions, reset its two-step sign-in, and
  disable or delete it. New accounts get a temporary password shown once,
  and the person picks their own at the first sign-in.
- A log ("Jurnal") of every change made in the dashboard: who, when and
  what, filterable by category and person. Entries cannot be edited or
  deleted.

- An Events tab ("Evenimente") with the faculty's events as cards: the
  picture on top, the title under it, then the day, time and place. The
  coming ones come first, then those of the last 60 days under "Trecute".
  An event opens in full with its description and a button for its link.
  The pictures stay on the phone, so the tab works offline after one sync.
  A new event can come as a notification, on its own channel.
- Events in the dashboard: a form with the picture (JPEG or PNG, re-encoded
  without the camera's location), day and time, place, description and
  link, a preview of the card and the page, and "Trimite notificare". The
  overview lists the next three events.
- New announcements arrive on Android phones as notifications, with the
  title and the start of the text. Tapping one opens the announcement. The
  first start asks for permission once, saying what notifications are for.
  While the app is open, a slim bar above the tabs shows the title instead,
  and tapping it opens the announcement. Several at once show as one bar
  with how many there are to read. Reading an announcement in the app
  clears its notification, and signing out clears them all and stops the
  phone receiving announcements (after signing out offline, from the next
  sync).
  The server sends them when the deployment sets a Firebase service account
  in `ETT_FCM_SERVICE_ACCOUNT`; without it nothing changes. When the
  server's clock is more than 5 minutes off, Google refuses every message,
  so the dashboard says so after publishing, with how far the clock is off.
- The dashboard can search the entire schedule as you type, filter it by
  day or to classes without a room or professor, and change rooms,
  professors or times for up to 200 selected classes. A preview shows
  booking conflicts, and stale confirmations cannot overwrite newer edits.
- Android has a Professors tab: a searchable list with current teaching or
  office hours, rooms, contact details and the weekly schedule.
- Announcements can be searched and filtered to unread on Android, with a
  mark-all-read action. Edited text becomes unread again. Dashboard
  pagination keeps older announcements accessible, and old pinned posts
  stay available beyond the phone's recent-post limit.

- Students can report an incorrect class from its details on Android.
  The dashboard keeps the class as it was reported, links to its editor,
  and lets the secretariat mark the report fixed or dismiss it. Each
  account can send five reports per hour, with one open report per class.
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
- A home screen widget, "Orarul de azi", in three sizes: the class now or
  next ("În 25 min"), the rest of the day, or today and the next day with
  classes. It works offline, follows the phone's light or dark theme and
  opens the app when tapped.
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
- The timetable can be corrected in the dashboard without a new import:
  subjects, single activities, professors with their office hours, rooms
  and buildings. One form sets the same teacher on all labs of a subject,
  which is how the lab assistants missing from the faculty's sheets get in.
- Administrators can find a student's account, give it a new password
  (which signs the student out everywhere) or delete it.

### Changed

- The dashboard has a new frame: the sections are grouped under Orar,
  Comunicare and Conturi with icons, the sidebar counts the open problem
  reports, and a bar on top shows the teaching week and the semester. A
  switch next to it turns on the dark theme, which otherwise follows the
  computer's setting.
- Long lists in the dashboard come in pages of 10, 25 or 50 rows, with
  "11-20 din 243" and links to every page. The student list no longer
  stops at 200 accounts, and searching it treats `%` and `_` as plain
  characters. Empty lists say what to do next. Gestionare orar shows 25
  classes per page and its search covers every page.
- Săli in the dashboard is a table of rooms (building, seats, classes and
  hours taught per week) with a search and a building filter, and a second
  tab for buildings; a building's name and address are edited on its own
  page instead of in 33 cards.
- Profesori in the dashboard shows each teacher's subjects, classes, hours
  per week and office, and filters those without an email or without
  classes. Materii shows the classes of each type, the teachers and the
  hours per week. Pages use the whole width of the screen.
- The dashboard's start page shows the classes running right now (or the
  next ones today) with their rooms and teachers, the open timetable
  problems, a to-do list from the data, the next events and, for
  administrators, the latest changes and a security summary. The tiles of
  bare numbers are gone.
- Problems reported by students open in a panel next to the list: the
  class as the student saw it, what changed in it since, what they wrote,
  other open reports about the same class, and the actions. The list can
  be searched, filtered by semester and exported as CSV.
- A dashboard error, a missing page or a page for administrators only now
  shows a short page in Romanian instead of JSON or a blank screen.
- The app stays in portrait when the phone is turned.
- Password fields on Android have a show or hide control. The password and
  confirmation fields are controlled separately when creating an account.
- The dashboard has a sidebar, page headers with their actions, groups
  listed by series and a cleaner look throughout.
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

- The dashboard accepted any number of wrong passwords. Five wrong
  passwords or codes for one account within 15 minutes now lock it for 15
  minutes.
- A dashboard form post with neither an `Origin` nor a `Referer` header was
  accepted. It is now refused, as are posts the browser marks as coming
  from another site.

- After adding another group's seminar or lab for the semester, the group's
  own one could not be removed, so both stayed in the timetable. Either can
  now be removed, as long as one weekly slot of that kind stays; a removed
  group slot can be added back.
- Coming back from a professor, a subject, an announcement or the profile
  reset the timetable to today's day view; it now stays on the day or week
  you were looking at, and the subjects search keeps its text.
- In the Romanian week view the "Azi" button was squeezed into a column of
  letters; the week arrows now sit next to the dates.
- One-hour classes no longer cut their text off: the card shows the name
  and one line with time, type and room.
- Room codes are written the same everywhere, without a space after the
  building ("BN212b", "L201 Polizu"), also when an admin imports them with
  spaces.
